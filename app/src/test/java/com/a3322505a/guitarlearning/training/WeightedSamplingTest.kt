package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class WeightedSamplingTest {
    @Test
    fun positionWithWeightTwoAppearsMoreOftenThanPositionWithWeightPointFive() {
        val highWeightId = "FretToNote:s1:f0"
        val lowWeightId = "FretToNote:s1:f1"
        val progress = listOf(
            Progress(
                knowledgeItemId = highWeightId,
                weight = 2.0,
            ),
            Progress(
                knowledgeItemId = lowWeightId,
                weight = 0.5,
            ),
        )
        val engine = TrainingEngine(
            settings = Settings(selectedStrings = setOf(1), fretStart = 0, fretEnd = 1),
            random = Random(41),
            progressProvider = { progress },
            enabledQuestionTypes = listOf(QuestionType.FretToNote),
        )
        val counts = mutableMapOf(highWeightId to 0, lowWeightId to 0)

        repeat(10_000) {
            val question = engine.generateQuestion(QuestionType.FretToNote)
            counts[question.knowledgeItemId] = counts.getValue(question.knowledgeItemId) + 1
        }

        assertTrue(counts.getValue(highWeightId) > counts.getValue(lowWeightId) * 3, counts.toString())
        assertTrue(counts.getValue(highWeightId) > 0, counts.toString())
        assertTrue(counts.getValue(lowWeightId) > 0, counts.toString())
    }

    @Test
    fun twoPhysicalPositionsWithTheSameNoteKeepIndependentWeights() {
        val factory = QuestionFactory()
        val firstStringC = factory.create(
            QuestionType.FretToNote,
            com.a3322505a.guitarlearning.core.GuitarCore.getFretPosition(1, 8),
        )
        val secondStringC = factory.create(
            QuestionType.FretToNote,
            com.a3322505a.guitarlearning.core.GuitarCore.getFretPosition(2, 1),
        )
        val progress = listOf(
            Progress(firstStringC.knowledgeItemId, weight = 0.4),
            Progress(secondStringC.knowledgeItemId, weight = 2.0),
        )
        val engine = TrainingEngine(
            settings = Settings(selectedStrings = setOf(1, 2), fretStart = 0, fretEnd = 8),
            random = Random(73),
            progressProvider = { progress },
            enabledQuestionTypes = listOf(QuestionType.FretToNote),
        )
        val counts = mutableMapOf(
            firstStringC.knowledgeItemId to 0,
            secondStringC.knowledgeItemId to 0,
        )

        repeat(30_000) {
            val id = engine.generateQuestion().knowledgeItemId
            if (id in counts) counts[id] = counts.getValue(id) + 1
        }

        assertTrue(firstStringC.knowledgeItemId != secondStringC.knowledgeItemId)
        assertTrue(firstStringC.note == "C" && secondStringC.note == "C")
        assertTrue(firstStringC.correctAnswerValue != secondStringC.correctAnswerValue)
        assertTrue(
            counts.getValue(secondStringC.knowledgeItemId) >
                counts.getValue(firstStringC.knowledgeItemId) * 3,
            counts.toString(),
        )
    }
}
