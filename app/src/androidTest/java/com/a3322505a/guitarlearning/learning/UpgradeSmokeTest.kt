package com.a3322505a.guitarlearning.learning

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlin.random.Random

/** Invoked in two separate instrumentation runs, with adb install -r between them. */
@RunWith(AndroidJUnit4::class)
class UpgradeSmokeTest {
    @Test fun seedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = LearningDatabase.open(context)
        try {
            val repo = RoomLearningRepository(db)
            val co = LearningCoordinator(LessonScheduler(Random(91)))
            var s = repo.load()
            s = repo.commit(s, co.start(s, "g00", 1000))
            var i = 0
            while (!Curriculum.mastered(s, "g00") && i < 30) {
                val task = s.active!!.task
                val answered = co.answer(s, coordinate = AnswerEvaluator.validPositions(task).first(), now = 2000L + i)
                s = repo.commit(s, answered)
                s = repo.commit(s, co.next(s, task.id, 3000L + i))
                i++
            }
            assertTrue(Curriculum.mastered(s, "g00"))
            s = repo.commit(s, s.copy(soundEnabled = false))
            context.filesDir.resolve("upgrade-expected.json").writeText(LearningCodec.encode(s))
            @Suppress("DEPRECATION")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            context.filesDir.resolve("upgrade-version.txt").writeText(version.toString())
            assertTrue(s.attempts.size >= 6)
        } finally { db.close() }
    }

    @Test fun verifyPreservedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = LearningCodec.decode(context.filesDir.resolve("upgrade-expected.json").readText())
        val db = LearningDatabase.open(context)
        try {
            val current = RoomLearningRepository(db).load()
            assertEquals(expected, current)
            assertEquals(expected.attempts.size, db.learningDao().attemptCount())
            assertFalse(current.soundEnabled)
            assertNotNull(current.active)
            assertTrue(Curriculum.mastered(current, "g00"))
            @Suppress("DEPRECATION")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            assertEquals(context.filesDir.resolve("upgrade-version.txt").readText().toInt() + 1, version)
        } finally { db.close() }
    }
}
