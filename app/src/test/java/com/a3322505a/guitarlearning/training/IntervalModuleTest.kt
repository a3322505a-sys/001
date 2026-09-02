package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IntervalModuleTest {
    private val module = IntervalModule()

    @Test
    fun levelsAreCumulativeAndLevelFourAddsNothing() {
        val banks = IntervalLevel.entries.associateWith(module::buildQuestionBank)

        assertTrue(banks.getValue(IntervalLevel.LV0).all { it.kind == "whole_half" })
        assertTrue(banks.getValue(IntervalLevel.LV1).containsAll(banks.getValue(IntervalLevel.LV0)))
        assertTrue(banks.getValue(IntervalLevel.LV2).containsAll(banks.getValue(IntervalLevel.LV1)))
        assertTrue(banks.getValue(IntervalLevel.LV3).containsAll(banks.getValue(IntervalLevel.LV2)))
        assertEquals(
            banks.getValue(IntervalLevel.LV3),
            banks.getValue(IntervalLevel.LV4),
        )
        assertTrue(banks.getValue(IntervalLevel.LV5).size > banks.getValue(IntervalLevel.LV4).size)
        assertTrue(banks.getValue(IntervalLevel.LV6).size > banks.getValue(IntervalLevel.LV5).size)
    }

    @Test
    fun examplesProduceTheRequiredExplanations() {
        val bank = module.buildQuestionBank(IntervalLevel.LV3)
        assertInterval(bank, "C", "E", 3, 4, "大三度")
        assertInterval(bank, "D", "F", 3, 3, "小三度")
        assertInterval(bank, "C", "G", 5, 7, "纯五度")
    }

    @Test
    fun concreteNotePairsKeepIndependentStableIds() {
        val bank = module.buildQuestionBank(IntervalLevel.LV2)
        val cToE = identify(bank, "C", "E")
        val dToF = identify(bank, "D", "F")

        assertNotEquals(cToE.knowledgeItemId, dToF.knowledgeItemId)
        assertEquals("interval:identify:C:E", cToE.knowledgeItemId)
        assertEquals("interval:identify:D:F", dToF.knowledgeItemId)
        assertEquals(cToE.knowledgeItemId, identify(module.buildQuestionBank(IntervalLevel.LV2), "C", "E").knowledgeItemId)
    }

    @Test
    fun generatedQuestionsHaveSafeChoicesAndExcludeSixSemitones() {
        val bank = module.buildQuestionBank(IntervalLevel.LV6)

        bank.forEach { question ->
            assertEquals(question.answerChoices.size, question.answerChoices.map { it.id }.distinct().size)
            assertTrue(question.answerChoices.any { it.id == question.correctChoiceId })
            assertTrue(question.choices.size in 2..3)
            val payload = question.payload as IntervalPayload
            assertFalse(payload.semitoneDistance == 6)
        }
        assertTrue(bank.none { it.correctAnswer.contains("增四") || it.correctAnswer.contains("减五") })
    }

    @Test
    fun lowerLevelsNeverLeakFutureIntervals() {
        val levelOne = module.buildQuestionBank(IntervalLevel.LV1)
        val levelTwo = module.buildQuestionBank(IntervalLevel.LV2)

        assertTrue(levelOne.none { (it.payload as IntervalPayload).degreeSpan == 3 })
        assertTrue(levelTwo.none { (it.payload as IntervalPayload).degreeSpan == 6 })
    }

    private fun assertInterval(
        bank: List<TrainingQuestion>,
        start: String,
        end: String,
        span: Int,
        semitones: Int,
        answer: String,
    ) {
        val question = identify(bank, start, end)
        val payload = question.payload as IntervalPayload
        assertEquals(span, payload.degreeSpan)
        assertEquals(semitones, payload.semitoneDistance)
        assertEquals(answer, question.correctAnswer)
    }

    private fun identify(
        bank: List<TrainingQuestion>,
        start: String,
        end: String,
    ): TrainingQuestion = bank.single {
        val payload = it.payload as IntervalPayload
        it.kind == "identify" && payload.startNote == start && payload.endNote == end
    }
}
