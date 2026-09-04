package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.audio.PitchCatalog
import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StaffTrainingTest {
    @Test
    fun allExercisesUseNaturalFirstPositionTargetsFromOnePitchSource() {
        val expectedSizes = mapOf(
            StaffExercise.SINGLE to 1..1,
            StaffExercise.SHORT_PHRASE to 2..4,
            StaffExercise.ONE_MEASURE to 6..8,
        )
        val machine = StaffTrainingStateMachine(random = Random(301))

        StaffExercise.entries.forEach { exercise ->
            val question = assertIs<StaffTrainingState.Awaiting>(
                machine.selectExercise(exercise),
            ).question
            assertTrue(question.targets.size in expectedSizes.getValue(exercise))
            question.targets.forEach { target ->
                assertTrue(target.position.fret in 0..4)
                assertTrue(GuitarCore.isNaturalNote(target.position.note))
                assertEquals(
                    PitchCatalog.forFretPosition(target.position).noteNumber,
                    target.soundingMidi,
                )
            }
        }
    }

    @Test
    fun trebleStaffUsesStandardGuitarOctaveDisplacement() {
        assertEquals(-7, writtenStaffStepForSoundingMidi(40))
        assertEquals(0, writtenStaffStepForSoundingMidi(52))
        assertEquals(7, writtenStaffStepForSoundingMidi(64))
        assertEquals(9, writtenStaffStepForSoundingMidi(67))
    }

    @Test
    fun continuousStaffNotesMustBeAnsweredInDisplayedOrder() {
        val machine = StaffTrainingStateMachine(
            initialExercise = StaffExercise.SHORT_PHRASE,
            random = Random(302),
        )
        val question = assertIs<StaffTrainingState.Awaiting>(machine.state).question

        question.targets.forEachIndexed { index, target ->
            val next = machine.submit(target.position)
            if (index == question.targets.lastIndex) {
                assertIs<StaffTrainingState.Completed>(next)
            } else {
                assertEquals(
                    question.targets.take(index + 1).map { it.position },
                    assertIs<StaffTrainingState.Awaiting>(next).selected,
                )
            }
        }
    }

    @Test
    fun wrongStaffPositionRequiresCorrectionAndManualAdvance() {
        val machine = StaffTrainingStateMachine(random = Random(303))
        val expected = assertIs<StaffTrainingState.Awaiting>(machine.state)
            .question.targets.single().position
        val wrong = GuitarCore.getFretPosition(
            string = if (expected.string == 1) 2 else 1,
            fret = expected.fret,
        )

        val correction = assertIs<StaffTrainingState.CorrectionRequired>(machine.submit(wrong))
        assertSame(correction, machine.submit(wrong))
        assertIs<StaffTrainingState.CorrectionConfirmed>(machine.submit(expected))
        assertIs<StaffTrainingState.Awaiting>(machine.nextQuestion())
    }
}
