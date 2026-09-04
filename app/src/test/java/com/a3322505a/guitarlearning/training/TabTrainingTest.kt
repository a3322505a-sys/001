package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabTrainingTest {
    @Test
    fun firstEntryUsesSixSingleNoteQuestionsThenFormalPhrases() {
        var guideCompleted = false
        val machine = TabTrainingStateMachine(
            guideCompleted = false,
            random = Random(201),
            onGuideCompleted = { guideCompleted = true },
        )

        repeat(TAB_GUIDE_QUESTION_COUNT) { index ->
            val awaiting = assertIs<TabTrainingState.Awaiting>(machine.state)
            assertTrue(awaiting.question.isGuide)
            assertEquals(1, awaiting.question.targets.size)
            machine.submit(awaiting.question.targets.single())
            assertIs<TabTrainingState.Completed>(machine.state)
            assertEquals(index == TAB_GUIDE_QUESTION_COUNT - 1, guideCompleted)
            machine.nextQuestion()
        }

        val formal = assertIs<TabTrainingState.Awaiting>(machine.state).question
        assertFalse(formal.isGuide)
        assertTrue(formal.targets.size in 3..4)
        assertTrue(formal.targets.all { it.string in 1..6 && it.fret in 0..4 })
    }

    @Test
    fun completedGuideStartsDirectlyWithFormalPhrase() {
        val question = assertIs<TabTrainingState.Awaiting>(
            TabTrainingStateMachine(
                guideCompleted = true,
                random = Random(202),
            ).state,
        ).question

        assertFalse(question.isGuide)
        assertTrue(question.targets.size in 3..4)
    }

    @Test
    fun formalPhraseRequiresTheDisplayedOrder() {
        val machine = TabTrainingStateMachine(
            guideCompleted = true,
            random = Random(203),
        )
        val question = assertIs<TabTrainingState.Awaiting>(machine.state).question

        question.targets.dropLast(1).forEachIndexed { index, target ->
            val progress = assertIs<TabTrainingState.Awaiting>(machine.submit(target))
            assertEquals(question.targets.take(index + 1), progress.selected)
        }
        val completed = assertIs<TabTrainingState.Completed>(
            machine.submit(question.targets.last()),
        )
        assertEquals(question.targets, completed.selected)
    }

    @Test
    fun wrongTapStopsUntilTheExpectedPositionIsConfirmed() {
        val machine = TabTrainingStateMachine(
            guideCompleted = true,
            random = Random(204),
        )
        val question = assertIs<TabTrainingState.Awaiting>(machine.state).question
        val expected = question.targets.first()
        val wrong = GuitarCore.getFretPosition(
            string = if (expected.string == 1) 2 else 1,
            fret = expected.fret,
        )

        val correction = assertIs<TabTrainingState.CorrectionRequired>(machine.submit(wrong))
        assertSame(correction, machine.submit(wrong))
        assertEquals(expected, correction.expected)
        assertIs<TabTrainingState.CorrectionConfirmed>(machine.submit(expected))
        assertIs<TabTrainingState.Awaiting>(machine.nextQuestion())
    }

    @Test
    fun oneMeasureContainsSixToEightOrderedFirstPositionTargets() {
        val machine = TabTrainingStateMachine(
            guideCompleted = true,
            random = Random(205),
        )

        val state = assertIs<TabTrainingState.Awaiting>(
            machine.selectExercise(TabExercise.ONE_MEASURE),
        )

        assertEquals(TabExercise.ONE_MEASURE, state.question.exercise)
        assertTrue(state.question.targets.size in 6..8)
        assertTrue(state.question.targets.all { it.string in 1..6 && it.fret in 0..4 })
        state.question.targets.forEach { machine.submit(it) }
        assertIs<TabTrainingState.Completed>(machine.state)
    }
}
