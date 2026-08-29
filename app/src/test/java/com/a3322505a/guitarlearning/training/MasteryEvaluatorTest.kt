package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.MasteryStatus
import com.a3322505a.guitarlearning.storage.Progress
import kotlin.test.Test
import kotlin.test.assertEquals

class MasteryEvaluatorTest {
    @Test
    fun masteryRequiresAFullRecentWindow() {
        assertEquals(
            MasteryStatus.UNLEARNED,
            MasteryEvaluator.evaluate(Progress(knowledgeItemId = "new")),
        )
        assertEquals(
            MasteryStatus.LEARNING,
            MasteryEvaluator.evaluate(
                Progress(
                    knowledgeItemId = "learning",
                    attempts = 19,
                    correct = 19,
                    recentResults = List(19) { true },
                ),
            ),
        )
    }

    @Test
    fun basicAndStableThresholdsAreDistinct() {
        val twentyWithNinetyPercent = List(18) { true } + List(2) { false }
        val basic = Progress(
            knowledgeItemId = "basic",
            attempts = 20,
            correct = 18,
            recentResults = twentyWithNinetyPercent,
            seenDays = setOf("2026-08-27"),
        )
        val stable = Progress(
            knowledgeItemId = "stable",
            attempts = 20,
            correct = 20,
            recentResults = List(20) { true },
            seenDays = setOf("2026-08-26", "2026-08-27"),
        )

        assertEquals(MasteryStatus.BASIC_MASTERY, MasteryEvaluator.evaluate(basic))
        assertEquals(MasteryStatus.STABLE_MASTERY, MasteryEvaluator.evaluate(stable))
        assertEquals(
            MasteryStatus.BASIC_MASTERY,
            MasteryEvaluator.evaluate(stable.copy(seenDays = setOf("2026-08-27"))),
        )
    }

    @Test
    fun exactFirstVersionWeightRulesCompose() {
        val weak = Progress(
            knowledgeItemId = "weak",
            mastery = MasteryStatus.LEARNING,
            recentResults = listOf(false),
        )
        val basic = Progress(
            knowledgeItemId = "basic",
            mastery = MasteryStatus.BASIC_MASTERY,
            recentResults = listOf(true),
        )
        val stable = Progress(
            knowledgeItemId = "stable",
            mastery = MasteryStatus.STABLE_MASTERY,
            recentResults = listOf(true),
        )

        assertEquals(8.0, QuestionWeights.forProgress(weak))
        assertEquals(1.0, QuestionWeights.forProgress(basic))
        assertEquals(0.5, QuestionWeights.forProgress(stable))
        assertEquals(1.0, QuestionWeights.forProgress(null))
    }
}
