package com.a3322505a.guitarlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.a3322505a.guitarlearning.ui.theme.AppTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningRepositoryTest {
    private fun openTest(context: Context, name: String): LearningDatabase =
        Room.databaseBuilder(context, LearningDatabase::class.java, name).allowMainThreadQueries().build()
    @Test fun downgradeTriggerCommitsAtomicallyAndSurvivesReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "downgrade-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val session = LearningSession(startedAt = 1)
        val task = LessonScheduler().makePosition("p01", Coordinate(1, 0), Direction.POSITION_TO_NOTE, TaskSource.MAIN)
            .copy(evidenceVersion = 1, adaptive = AdaptiveTask(AdaptiveRun().config, PracticePurpose.NORMAL))
        var saved = repo.commit(repo.load(), LearnerState(currentNode = "p01", sessionId = session.id, sessions = listOf(session),
            active = ActiveTask(task), regionTraining = RegionRun("LOW", 1, 0)))
        saved = repo.commit(saved, co.answer(saved, symbol = "F", now = 100))
        saved = repo.commit(saved, saved.copy(active = ActiveTask(task.copy(id = newId()))))
        val changed = co.answer(saved, symbol = "F", now = 200)
        assertTrue(changed.regionTraining!!.adaptive.scaffolding)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_downgrade BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(saved, changed) }
        assertEquals(saved, repo.load())
        assertEquals(1, db.learningDao().attemptCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_downgrade")
        saved = repo.commit(saved, changed)
        db.close(); db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(2, db.learningDao().attemptCount())
        val malformed = saved.copy(regionTraining = saved.regionTraining!!.copy(adaptive = saved.regionTraining!!.adaptive.copy(diagnosing = false)))
        assertFails { repo.restore(saved, LearningCodec.encode(malformed)) }
        assertEquals(saved, repo.load())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun familyMemberFailureAndConfigurationCommitAtomicallyAndRestoreRejectsInvalidRun() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "family-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val initial = repo.load()
        val session = LearningSession(startedAt = 1)
        val base = initial.copy(currentNode = "tab02", sessionId = session.id, sessions = listOf(session),
            introductions = setOf("reading:tab02:intro"))
        val seed = ReadingLessons.phrase("tab02", ReadingLessons.positions.take(3), TaskSource.MAIN)
        val scope = FamilyAdaptation.scope(base, seed)
        val task = seed.copy(adaptive = AdaptiveTask(AdaptiveRun(mixStage = 1).config, PracticePurpose.NORMAL, 1, familyScope = scope))
        val started = repo.commit(initial, AdaptiveEvidence.present(base, task, 10))
        val changed = LearningCoordinator().answer(started, coordinate = Coordinate(1, 2), now = 20)
        assertEquals(1, changed.weakPoints.size)
        assertEquals(1, changed.attempts.single().members.size)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_family BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(started, changed) }
        assertEquals(started, repo.load())
        assertEquals(0, db.learningDao().evidenceCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_family")
        val saved = repo.commit(started, changed)
        db.close(); db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(1, db.learningDao().evidenceCount())
        assertFails { repo.commit(started, changed) }
        val bad = saved.copy(familyRuns = saved.familyRuns.mapValues { (_, c) -> c.copy(run = c.run.copy(mixStage = 2)) })
        assertFails { repo.restore(saved, LearningCodec.encode(bad)) }
        assertEquals(saved, repo.load())
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun adaptiveFailureAndExposureRollBackTogetherAndMalformedRestoreKeepsTheProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "adaptive-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val initial = repo.load()
        val c = Coordinate(1, 0)
        val task = LessonScheduler().makePosition("p01", c, Direction.POSITION_TO_NOTE, TaskSource.MAIN)
            .copy(evidenceVersion = 1, adaptive = AdaptiveTask(AdaptiveRun().config, PracticePurpose.NORMAL))
        val session = LearningSession(startedAt = 1)
        val started = repo.commit(initial, initial.copy(currentNode = "p01", sessionId = session.id,
            sessions = listOf(session), active = ActiveTask(task), regionTraining = RegionRun("LOW", 1, 0)))
        val next = LearningCoordinator().answer(started, symbol = task.options.first { it != task.constraint.symbol }, now = 2000)
        assertFalse(next.attempts.single().firstCorrect!!)
        assertTrue(next.knowledgeExposures.isNotEmpty())
        assertTrue(next.weakPoints.isNotEmpty())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_adaptive BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(started, next) }
        assertEquals(started, repo.load())
        assertEquals(0, db.learningDao().attemptCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_adaptive")
        val saved = repo.commit(started, next)
        db.close(); db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(listOf(false), AdaptiveEvidence.View(repo.load(), 2001).samples.map { it.correct })
        assertFails { repo.commit(started, next) }
        val bad = saved.copy(regionTraining = saved.regionTraining!!.copy(adaptive = AdaptiveRun(mixStage = 4)))
        assertFails { repo.restore(saved, LearningCodec.encode(bad)) }
        assertEquals(saved, repo.load())
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun closeReopenKeepsTaskProfileSettingsAndEvidenceExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        var state = repo.load()
        state = repo.commit(state, co.start(state, "g00", 1000))
        val task = state.active!!.task
        state = repo.commit(state, co.answer(state, Coordinate(1, 4), now = 2000))
        state = repo.commit(state, state.copy(soundEnabled = false, themeId = AppTheme.FOREST.id))
        val id = state.learnerId
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertEquals(state, repo.load())
        assertEquals(id, repo.load().learnerId)
        assertEquals(1, db.learningDao().attemptCount())
        val repeat = co.answer(state, Coordinate(1, 4), now = 3000)
        state = repo.commit(state, repeat)
        assertEquals(task.id, state.active!!.task.id)
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun transactionFailureRollsBackEvidenceAndCanRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        val db = openTest(context, name)
        val repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val started = repo.commit(initial, co.start(initial, "g00", 1000))
        val next = co.answer(started, Coordinate(1, 2), now = 2000).copy(themeId = AppTheme.MIDNIGHT.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_commit BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(started, next) }
        assertEquals(started, repo.load())
        assertEquals(0, db.learningDao().attemptCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_commit")
        val saved = repo.commit(started, next)
        assertEquals(1, db.learningDao().attemptCount())
        assertFails { repo.commit(started, next) }
        assertEquals(saved, repo.load())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun invalidRestoreCannotEraseValidProfileAndValidBackupRoundTrips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        val db = openTest(context, name)
        val repo = RoomLearningRepository(db)
        val initial = repo.load()
        val saved = repo.commit(initial, LearningCoordinator().start(initial, "g00", 1000))
        assertFails { repo.restore(saved, "{broken}") }
        assertFails { repo.restore(saved, LearningCodec.encode(saved).replace("\"schemaVersion\":1", "\"schemaVersion\":999")) }
        assertEquals(saved, repo.load())
        val restored = repo.restore(saved, LearningCodec.encode(saved))
        assertEquals(saved.copy(revision = saved.revision + 1), restored)
        db.close(); context.deleteDatabase(name)
    }

    @Test fun oldSnapshotAndBackupUseDefaultThemeWithoutLosingLearningData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val started = repo.commit(initial, co.start(initial, "g00", 1000))
        val saved = repo.commit(started, co.answer(started, Coordinate(1, 2), now = 2000))
        val oldFields = LearningCodec.json.parseToJsonElement(LearningCodec.encode(saved)).jsonObject - "themeId"
        val oldBackup = JsonObject(oldFields).toString()
        db.learningDao().saveSnapshot(SnapshotEntity(revision = saved.revision, json = oldBackup))
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(AppTheme.CLEAR.id, repo.load().themeId)
        assertEquals(saved.attempts.size, db.learningDao().attemptCount())
        val themed = repo.commit(saved, saved.copy(themeId = AppTheme.GRAPHITE.id))
        val roundTrip = repo.restore(themed, LearningCodec.encode(themed))
        assertEquals(themed.copy(revision = themed.revision + 1), roundTrip)
        val restoredOld = repo.restore(roundTrip, oldBackup)
        assertEquals(saved.copy(revision = roundTrip.revision + 1), restoredOld)
        val future = repo.restore(restoredOld, LearningCodec.encode(restoredOld.copy(themeId = "future-theme")))
        assertEquals(AppTheme.CLEAR, AppTheme.fromId(future.themeId))
        assertEquals(restoredOld.copy(revision = future.revision, themeId = "future-theme"), repo.load())
        assertEquals(saved.attempts.size, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun mappingContextAndPartialCorrectionPersistWithoutDuplicatingAttempts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val initial = repo.load()
        val task = MappingLessons.make("C", Direction.NOTE_TO_DEGREE, TaskSource.MAIN, tonic = 7)
        val session = LearningSession(startedAt = 1)
        var state = repo.commit(initial, initial.copy(currentNode = "mapping", sessions = listOf(session), sessionId = session.id, active = ActiveTask(task)))
        val co = LearningCoordinator()
        state = repo.commit(state, co.answer(state, symbol = "1", now = 2))
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertEquals(state, repo.load())
        assertEquals(7, state.active!!.task.tonicPitchClass)
        state = repo.commit(state, co.answer(state, symbol = "4", now = 3))
        assertEquals(Phase.CORRECTED, state.active!!.phase)
        assertEquals(false, state.attempts.single().firstCorrect)
        assertEquals(1, db.learningDao().attemptCount())
        assertFails { repo.restore(state, LearningCodec.encode(state.copy(active = state.active!!.copy(task = task.copy(tonicPitchClass = 99))))) }
        assertEquals(state, repo.load())
        val restored = repo.restore(state, LearningCodec.encode(state))
        assertEquals(state.copy(revision = state.revision + 1), restored)
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun practiceAndSuspendedLessonCommitTogetherOrBothRollBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val student = initial.copy(progress = listOf("g00", "n00").associateWith { NodeProgress(masteredAt = 1) },
            introductions = setOf("position:s1:f0", "position:s1:f1"))
        val lesson = repo.commit(initial, co.start(student, "p01", 10))
        val practice = co.startPractice(lesson, PracticePlan(listOf("p01"), PracticeKind.POSITION_MIXED), 11)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_practice BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(lesson, practice) }
        assertEquals(lesson, repo.load())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_practice")
        val saved = repo.commit(lesson, practice)
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        val restored = repo.restore(saved, LearningCodec.encode(saved))
        val ended = repo.commit(restored, co.end(restored, 20))
        assertEquals(lesson.active, ended.active)
        assertEquals(lesson.sessionId, ended.sessionId)
        assertNull(ended.practice)
        assertEquals(0, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun partialChordMembersPersistAndFailedAppendCannotCreatePhantomEvidence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val task = ChordLessons.make(ChordShapes.am, "chord-am", TaskSource.DEMONSTRATION).copy(source = TaskSource.MAIN)
        val session = LearningSession(startedAt = 1)
        var state = repo.commit(initial, initial.copy(currentNode = "chord-am", active = ActiveTask(task), sessions = listOf(session), sessionId = session.id, fingeringMode = "numbers"))
        state = repo.commit(state, co.answer(state, symbol = "X", now = 2))
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertEquals(state, repo.load())
        assertEquals(1, db.learningDao().evidenceCount())
        val next = co.answer(state, coordinate = Coordinate(5, 0), now = 3)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_member BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(state, next) }
        assertEquals(state, repo.load())
        assertEquals(1, db.learningDao().evidenceCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_member")
        state = repo.commit(state, next)
        assertEquals(2, db.learningDao().evidenceCount())
        val restored = repo.restore(state, LearningCodec.encode(state))
        assertEquals(state.copy(revision = state.revision + 1), restored)
        assertEquals(2, db.learningDao().evidenceCount())
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun partialStaffPhraseRollbackAndRestoreKeepActualEquivalentCoordinate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${newId()}.db"
        val db = openTest(context, name)
        val repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val task = ReadingLessons.phrase("staff02", listOf(Coordinate(1, 0), Coordinate(2, 0)), TaskSource.MAIN)
        val session = LearningSession(startedAt = 1)
        var state = repo.commit(initial, initial.copy(currentNode = "staff02", active = ActiveTask(task), sessions = listOf(session), sessionId = session.id))
        state = repo.commit(state, co.answer(state, Coordinate(1, 0), now = 2))
        val next = co.answer(state, Coordinate(3, 4), now = 3)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reading BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(state, next) }
        assertEquals(1, repo.load().active!!.sequenceIndex)
        assertEquals(1, db.learningDao().evidenceCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reading")
        state = repo.commit(state, next)
        val restored = repo.restore(state, LearningCodec.encode(state))
        assertEquals(Coordinate(3, 4), restored.attempts.single().members.last().coordinate)
        assertEquals(2, db.learningDao().evidenceCount())
        assertEquals(1, db.learningDao().attemptCount())
        val invalid = state.copy(active = state.active!!.copy(task = task.copy(notation = NotationPrompt(NotationKind.STAFF, listOf(64, 60)))))
        assertFails { repo.restore(restored, LearningCodec.encode(invalid)) }
        assertEquals(restored, repo.load())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun playbackAndPartialScaleSaveTogetherOrRollBackWithoutEvidence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-"+newId()+".db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val task = StructureLessons.tasks("ear-triads").first().copy(id = newId())
        val session = LearningSession(startedAt = 1)
        var state = repo.commit(initial, initial.copy(currentNode = "ear-triads", active = ActiveTask(task), sessions = listOf(session), sessionId = session.id))
        val played = co.playbackCompleted(state, task.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_audio BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(state, played) }
        assertFalse(repo.load().active!!.audioReady)
        assertEquals(0, db.learningDao().evidenceCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_audio")
        state = repo.commit(state, played)
        db.close()
        db = openTest(context, name)
        repo = RoomLearningRepository(db)
        assertTrue(repo.load().active!!.audioReady)
        state = repo.commit(state, co.answer(state, symbol = task.constraint.symbol, now = 2))
        assertTrue(state.attempts.single().audioPlayed)
        assertEquals(1, db.learningDao().evidenceCount())
        val invalid = state.copy(attempts = listOf(state.attempts.single().copy(audioPlayed = false)))
        assertFails { repo.restore(state, LearningCodec.encode(invalid)) }
        assertEquals(state, repo.load())
        val scale = StructureLessons.tasks("scale-major").first { it.completion == CompletionKind.SEQUENCE }.copy(id = newId())
        state = repo.commit(state, state.copy(currentNode = "scale-major", active = ActiveTask(scale)))
        state = repo.commit(state, co.answer(state, AnswerEvaluator.validPositions(scale).last(), now = 3))
        val restored = repo.restore(state, LearningCodec.encode(state))
        assertEquals(1, restored.active!!.sequenceIndex)
        assertEquals(1, restored.attempts.last().members.size)
        assertEquals(2, db.learningDao().evidenceCount())
        db.close(); context.deleteDatabase(name)
    }


    @Test fun regionProbeAndQueuedBoundaryRollbackTogetherWithAttempt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "region-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val base = initial.copy(progress = mapOf("g00" to NodeProgress(1), "n00" to NodeProgress(1)),
            introductions = setOf("position:s1:f0", "position:s1:f1"))
        val started = repo.commit(initial, co.startRegion(base, "LOW", 1000))
        val t = started.active!!.task
        val next = if (t.constraint.kind == ConstraintKind.SYMBOL) co.answer(started, symbol = t.constraint.symbol, now = 2000)
            else co.answer(started, coordinate = AnswerEvaluator.validPositions(t).first(), now = 2000)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_commit BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(started, next) }
        assertEquals(started, repo.load())
        assertEquals(0, db.learningDao().attemptCount())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_commit")
        val saved = repo.commit(started, next)
        db.close()
        db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(saved.active, co.startRegion(repo.load(), "LOW", 3000).active)
        assertEquals(1, db.learningDao().attemptCount())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun switchingRegionRollsBackAsOneTransactionAndKeepsPausedTaskAfterRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "region-switch-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val initial = repo.load()
        val base = initial.copy(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
            introductions = setOf("position:s1:f0", "position:s1:f1"))
        val started = repo.commit(initial, co.start(base, "chord-am", 10))
        val partial = repo.commit(started, co.answer(started, coordinate = Coordinate(6, 4), now = 20))
        val switched = co.startRegion(partial, "LOW", 30)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_switch BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(partial, switched) }
        assertEquals(partial, repo.load())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_switch")
        val saved = repo.commit(partial, switched)
        db.close()
        db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(saved, repo.load())
        assertEquals(partial.active, co.start(repo.load(), "chord-am", 40).active)
        val damaged = saved.copy(pausedTraining = saved.pausedTraining!!.copy(sessionId = "missing-session"))
        assertFails { repo.restore(saved, LearningCodec.encode(damaged)) }
        assertEquals(saved, repo.load())
        db.close(); context.deleteDatabase(name)
    }

    @Test fun roundEndAndContinuationRollBackTogetherThenRetryOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "round-end-${newId()}.db"
        var db = openTest(context, name)
        var repo = RoomLearningRepository(db)
        val co = LearningCoordinator()
        val base = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
            introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet())
        val active = co.startRegion(base, "LOW", 10).let { it.copy(regionTraining = it.regionTraining!!.copy(
            adaptive = AdaptiveRun(diagnosing = true, scaffolding = true, generation = 2))) }
        val saved = repo.commit(repo.load(), active)
        val ended = co.end(saved, 20)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_end BEFORE INSERT ON learner_snapshot BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertFails { repo.commit(saved, ended) }
        assertEquals(saved, repo.load())
        assertNull(repo.load().sessions.last().endedAt)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_end")
        val committed = repo.commit(saved, ended)
        db.close(); db = openTest(context, name); repo = RoomLearningRepository(db)
        assertEquals(committed, repo.load())
        assertEquals(committed, co.end(committed, 30))
        val restarted = co.startRegion(repo.load(), "LOW", 40)
        assertTrue(restarted.regionTraining!!.adaptive.scaffolding)
        assertEquals(1, restarted.active!!.task.roundSlot)
        db.close(); context.deleteDatabase(name)
    }

}
