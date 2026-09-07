package com.a3322505a.guitarlearning.learning

import android.content.Context
import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "learner_snapshot")
data class SnapshotEntity(@PrimaryKey val id: Int = 1, val revision: Long, val json: String)

@Entity(tableName = "attempts")
data class AttemptEntity(@PrimaryKey val taskId: String, val learnerId: String, val sessionId: String, val nodeId: String, val at: Long, val json: String)

@Entity(tableName = "skill_evidence")
data class EvidenceEntity(@PrimaryKey val id: String, val taskId: String, val skillId: String, val correct: Boolean, val independent: Boolean)

@Entity(tableName = "node_progress")
data class NodeEntity(@PrimaryKey val nodeId: String, val masteredAt: Long?, val needsReview: Boolean, val retainedOn: String?)

@Entity(tableName = "learning_sessions")
data class SessionEntity(@PrimaryKey val id: String, val startedAt: Long, val endedAt: Long?)

@Dao
interface LearningDao {
    @Query("SELECT * FROM learner_snapshot WHERE id = 1") fun snapshot(): SnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveSnapshot(value: SnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveAttempts(values: List<AttemptEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveEvidence(values: List<EvidenceEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveNodes(values: List<NodeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveSessions(values: List<SessionEntity>)
    @Query("SELECT COUNT(*) FROM attempts") fun attemptCount(): Int
    @Query("SELECT COUNT(*) FROM skill_evidence") fun evidenceCount(): Int
    @Query("DELETE FROM attempts") fun clearAttempts()
    @Query("DELETE FROM skill_evidence") fun clearEvidence()
    @Query("DELETE FROM node_progress") fun clearNodes()
    @Query("DELETE FROM learning_sessions") fun clearSessions()
}

@Database(entities = [SnapshotEntity::class, AttemptEntity::class, EvidenceEntity::class, NodeEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    companion object {
        fun open(context: Context, name: String = "learning-v2.db"): LearningDatabase =
            Room.databaseBuilder(context.applicationContext, LearningDatabase::class.java, name).build()
        // No destructive migration fallback: an unknown schema must fail while preserving data.
    }
}

object LearningCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun encode(state: LearnerState): String = json.encodeToString(state)
    fun decode(text: String): LearnerState {
        val state = json.decodeFromString<LearnerState>(text)
        require(state.schemaVersion == 1) { "学习档案版本不受支持。" }
        require(state.revision >= 0 && state.learnerId.isNotBlank())
        require(Curriculum.nodes.any { it.id == state.currentNode })
        require(state.attempts.map { it.task.id }.distinct().size == state.attempts.size)
        require(state.sessions.map { it.id }.distinct().size == state.sessions.size)
        require(state.attempts.all { a -> a.ordinal > 0 && state.sessions.any { it.id == a.sessionId } })
        require(state.pilotResults.map { it.mode to it.clip }.distinct().size == state.pilotResults.size)
        require(state.knowledgeExposures.distinct().size == state.knowledgeExposures.size)
        require(state.knowledgeExposures.all { it.taskId.isNotBlank() && it.target.isNotBlank() && it.at >= 0 })
        require(state.weakPoints.all { (key, point) -> key == point.unit && point.target.isNotBlank() && point.observedAt >= 0 &&
            (point.confirmedAt == null || point.confirmedAt >= point.observedAt) && (point.resolvedAt == null || point.resolvedAt >= point.observedAt) })
        require(state.familyRuns.all { (key, context) ->
            val run = context.run
            key.startsWith("${context.sessionId}/${context.nodeId}") && state.sessions.any { it.id == context.sessionId } &&
                Curriculum.nodes.any { it.id == context.nodeId } && run.generation >= 0 && run.sinceOrdinal >= 0 && run.diagnosisSince >= 0 &&
                run.mixStage in 0..1 && run.focus.distinct().size == run.focus.size && (!run.diagnosing || run.mixStage == 0 && !run.trial)
        })
        val paused = listOfNotNull(state.pausedTraining) + state.pausedRegions.values
        require(state.pausedRegions.all { (id, p) -> id in FretboardRegion.entries.map { it.name } && p.regionTraining?.regionId == id && p.practice == null && p.pilot == null })
        val contexts = listOf(state) + paused.map { RegionSessions.restore(state, it) }
        contexts.forEach { validateContext(it) }
        (state.attempts.map { it.task } + contexts.flatMap { listOfNotNull(it.active?.task, it.suspendedLesson?.active?.task, it.pilotSuspended?.active?.task) }).forEach { task ->
            val constraints = listOf(task.constraint) + task.sequence
            require(constraints.all { it.allowedPitches.all { p -> p in 40..88 } && (it.kind != ConstraintKind.PITCH_SET || it.allowedPitches.isNotEmpty()) })
            require(constraints.all { (it.firstFret == null || it.firstFret in 0..15) && (it.lastFret == null || it.lastFret in (it.firstFret ?: 0)..15) })
            require(task.auditoryScore == null || task.relation?.ear == true)
            require(task.creationDurations.isEmpty() || task.creationDurations.size == task.sequence.size && task.creationDurations.all { it > 0 })
            require(task.chordProgression.all { it.isNotEmpty() && it.all { p -> p in 40..88 } })
            task.relation?.let { relation ->
                require((task.direction == Direction.REFERENCE_EAR) == relation.ear)
                if (task.completion == CompletionKind.SEQUENCE) require(task.sequence.map { it.midi } == relation.targetPitches)
            }
            task.notation?.let { notation ->
                val rules = if (task.completion == CompletionKind.SEQUENCE) task.sequence else listOf(task.constraint)
                if (rules.first().kind != ConstraintKind.SYMBOL) {
                    require(rules.size == notation.pitches.size)
                    rules.forEachIndexed { index, rule -> require(if (notation.kind == NotationKind.TAB)
                        rule.kind == ConstraintKind.COORDINATE && rule.coordinate == notation.coordinates[index]
                        else rule.kind == ConstraintKind.PITCH && rule.midi == notation.pitches[index]) }
                }
            }
            require(task.targetSkillIds.isEmpty() || task.targetSkillIds.size == task.sequence.size)
            require(task.tonicPitchClass == null || task.tonicPitchClass in 0..11)
            require(task.evidenceVersion >= 0)
            require(task.explanationTargets.all { it.isNotBlank() })
            task.adaptive?.let { adaptive ->
                require(adaptive.stage in 0..3 && adaptive.config.isNotBlank())
                adaptive.familyScope?.let { scope -> require(scope in state.familyRuns && state.familyRuns.getValue(scope).nodeId == task.nodeId && adaptive.stage in 0..1) }
                if (adaptive.options.isNotEmpty()) {
                    require(task.direction == Direction.POSITION_TO_NOTE && task.coordinate != null && task.completion == CompletionKind.SINGLE)
                    require(adaptive.options.map { it.label } == task.options)
                    require(adaptive.options.map { it.pitchClass }.distinct().size == adaptive.options.size && adaptive.options.size >= 2)
                    require(adaptive.options.map { it.label }.distinct().size == adaptive.options.size)
                    require(adaptive.options.all { it.pitchClass in 0..11 })
                    require(adaptive.options.all { option ->
                        val note = com.a3322505a.guitarlearning.core.MusicFacts.noteNames[option.pitchClass]
                        option.label == when (option.representation) {
                            AnswerRepresentation.NOTE -> note
                            AnswerRepresentation.FIXED -> com.a3322505a.guitarlearning.core.MusicFacts.fixedSolfege[note]
                            AnswerRepresentation.DEGREE -> task.tonicPitchClass?.let { com.a3322505a.guitarlearning.core.MusicFacts.majorDegree(option.pitchClass, it)?.toString() }
                        }
                    })
                    val answer = adaptive.options.single { it.pitchClass == com.a3322505a.guitarlearning.core.MusicFacts.midi(task.coordinate.string, task.coordinate.fret) % 12 }
                    require(task.constraint.symbol == answer.label && adaptive.correctRepresentation == answer.representation)
                    require(adaptive.options.none { it.representation == AnswerRepresentation.DEGREE } || task.tonicPitchClass != null && task.tonalMode == "major")
                }
            }
            if (task.direction in MappingLessons.directions) {
                require(task.mappingNote in MappingLessons.notes)
                if (task.direction in MappingLessons.degreeDirections) require(task.tonicPitchClass != null && task.tonalMode == "major")
            }
        }
        state.attempts.forEach { a ->
            require(a.task.relation?.ear != true || !a.independent || a.audioPlayed)
            require(a.task.relation?.ear != true || a.firstUnassisted != true || a.audioPlayed)
            require(a.members.map { it.index }.distinct().size == a.members.size)
            require(a.members.all { it.index in a.task.sequence.indices && a.task.targetSkillIds.getOrNull(it.index) == it.skillId })
            require(a.members.all { m -> m.firstUnassisted != true || !a.task.guided && (a.task.relation?.ear != true || a.audioPlayed) })
        }
        return state
    }

    private fun validateContext(state: LearnerState) {
        require(Curriculum.nodes.any { it.id == state.currentNode })
        require(state.sessionId == null || state.sessions.any { it.id == state.sessionId && it.endedAt == null })
        require(state.active == null || state.sessionId != null)
        require((state.practice == null) == (state.suspendedLesson == null))
        require((state.pilot == null) == (state.pilotSuspended == null))
        state.pilot?.let { run ->
            require(run.clip in 0..7 && run.bpm in 40..80 && run.elapsedMs >= 0 && run.playbackMs >= 0)
            require(state.active?.task?.notation?.score == run.score)
        }
        state.pilotSuspended?.let { old ->
            require(Curriculum.nodes.any { it.id == old.currentNode })
            require(old.sessionId == null || state.sessions.any { it.id == old.sessionId && it.endedAt == null })
        }
        state.regionTraining?.let { run ->
            require(run.regionId in FretboardRegion.entries.map { it.name } && run.startOrdinal > 0 && run.probeSize in 0..5)
            require(run.adaptive.generation >= 0 && run.adaptive.sinceOrdinal >= 0 && run.adaptive.diagnosisSince >= 0 && run.adaptive.mixStage in 0..3)
            require(AnswerRepresentation.NOTE !in run.adaptive.excluded)
            require(run.adaptive.representatives.distinct().size == run.adaptive.representatives.size && run.adaptive.representatives.all { it.fret <= 4 })
            require(run.adaptive.focus.distinct().size == run.adaptive.focus.size)
            require(run.adaptive.representationTrials.values.all { it > 0 })
        }
        require(state.queuedRegion == null || state.queuedRegion in FretboardRegion.entries.map { it.name })
        state.practice?.let { plan ->
            require(state.sessionId != null && plan.nodeIds.isNotEmpty() && plan.nodeIds.distinct().size == plan.nodeIds.size)
            require(plan.nodeIds.all { id -> Curriculum.nodes.any { it.id == id } })
        }
        state.suspendedLesson?.let { lesson ->
            require(Curriculum.nodes.any { it.id == lesson.currentNode })
            require(lesson.sessionId == null || state.sessions.any { it.id == lesson.sessionId && it.endedAt == null })
            require(lesson.active == null || lesson.sessionId != null)
        }
    }
}

interface LearningRepository {
    fun load(): LearnerState
    fun commit(previous: LearnerState, next: LearnerState): LearnerState
    fun restore(previous: LearnerState, backup: String): LearnerState
}

class RoomLearningRepository(private val db: LearningDatabase) : LearningRepository {
    override fun load(): LearnerState = db.learningDao().snapshot()?.let { LearningCodec.decode(it.json) } ?: LearnerState()

    override fun commit(previous: LearnerState, next: LearnerState): LearnerState = write(previous, next, false)

    override fun restore(previous: LearnerState, backup: String): LearnerState {
        val validated = LearningCodec.decode(backup)
        return write(previous, validated, true)
    }

    private fun write(previous: LearnerState, next: LearnerState, restoring: Boolean): LearnerState {
        val saved = next.copy(revision = previous.revision + 1)
        val encoded = LearningCodec.encode(saved)
        db.runInTransaction {
            val dao = db.learningDao()
            check((dao.snapshot()?.revision ?: 0L) == previous.revision) { "学习记录已变化，请重新读取后重试。" }
            if (restoring) { dao.clearAttempts(); dao.clearEvidence(); dao.clearNodes(); dao.clearSessions() }
            val changed = if (restoring) saved.attempts else saved.attempts.filter { a -> previous.attempts.firstOrNull { it.task.id == a.task.id } != a }
            dao.saveAttempts(changed.map { AttemptEntity(it.task.id, saved.learnerId, it.sessionId, it.task.nodeId, it.at, LearningCodec.json.encodeToString(it)) })
            dao.saveEvidence(changed.flatMap { a ->
                if (a.task.completion == CompletionKind.SINGLE && a.firstCorrect != null)
                    listOf(EvidenceEntity("${a.task.id}:${a.task.skillId}", a.task.id, a.task.skillId, a.firstCorrect == true, a.independent))
                else a.members.map { m -> EvidenceEntity("${a.task.id}:member:${m.index}:${m.skillId}", a.task.id, m.skillId, m.firstCorrect, m.independent) }
            })
            dao.saveNodes(saved.progress.map { (id, p) -> NodeEntity(id, p.masteredAt, p.needsReview, p.retainedOn) })
            dao.saveSessions(saved.sessions.map { SessionEntity(it.id, it.startedAt, it.endedAt) })
            dao.saveSnapshot(SnapshotEntity(revision = saved.revision, json = encoded))
        }
        return saved
    }
}
