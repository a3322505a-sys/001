package com.a3322505a.guitarlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningRepositoryTest {
    private fun openTest(context: Context, name: String): LearningDatabase =
        Room.databaseBuilder(context, LearningDatabase::class.java, name).allowMainThreadQueries().build()
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
        state = repo.commit(state, state.copy(soundEnabled = false))
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
        val next = co.answer(started, Coordinate(1, 2), now = 2000)
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
}
