package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.audio.PitchPlaybackStyle
import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CombinedMappingTrainingTest {
    @Test
    fun generatedQuestionsCoverAllSixPathsAndEveryAnswerDomain() {
        val factory = CombinedQuestionFactory(Random(20260830))
        val paths = buildSet {
            repeat(600) {
                val question = factory.create()
                add(
                    listOf(
                        question.sourceType,
                        question.firstTargetType,
                        question.secondTargetType,
                    ),
                )
                AnswerKind.entries.forEach { kind ->
                    val choices = factory.shuffledAnswers(kind)
                    assertEquals(7, choices.size)
                    assertEquals(7, choices.distinct().size)
                    assertTrue(question.valueFor(kind) in choices)
                }
            }
        }

        assertEquals(6, paths.size)
    }

    @Test
    fun twoCorrectStepsCountAsExactlyOneCorrectQuestion() {
        val machine = CombinedMappingStateMachine(Random(11))
        val first = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)

        val firstCorrect = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(first.correctAnswer),
        )
        assertTrue(!firstCorrect.completesQuestion)
        assertEquals(0, machine.correctCount)
        assertEquals(0, machine.errorCount)

        val second = assertIs<CombinedMappingState.AwaitingAnswer>(
            machine.advanceAfterCorrect(),
        )
        assertEquals(CombinedStep.SECOND, second.step)
        assertEquals(first.question.id, second.question.id)
        assertTrue(second.prompt.startsWith(first.question.valueFor(first.question.sourceType)))
        assertTrue(second.prompt.contains(first.question.valueFor(first.question.firstTargetType)))

        val complete = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(second.correctAnswer),
        )
        assertTrue(complete.completesQuestion)
        assertEquals(1, machine.correctCount)
        assertEquals(0, machine.errorCount)

        val next = assertIs<CombinedMappingState.AwaitingAnswer>(machine.advanceAfterCorrect())
        assertNotEquals(first.question.id, next.question.id)
        assertEquals(CombinedStep.FIRST, next.step)
    }

    @Test
    fun anErrorAtEitherStepEndsTheQuestionAndCountsOnlyOnce() {
        val machine = CombinedMappingStateMachine(Random(12))
        val first = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)
        val wrong = first.choices.first { it != first.correctAnswer }

        val incorrect = assertIs<CombinedMappingState.Incorrect>(machine.submitAnswer(wrong))
        assertEquals(1, machine.errorCount)
        assertEquals(0, machine.correctCount)
        assertSame(incorrect, machine.submitAnswer(first.correctAnswer))
        assertEquals(1, machine.errorCount)

        val next = assertIs<CombinedMappingState.AwaitingAnswer>(machine.nextQuestion())
        assertNotEquals(first.question.id, next.question.id)

        assertIs<CombinedMappingState.Correct>(machine.submitAnswer(next.correctAnswer))
        val second = assertIs<CombinedMappingState.AwaitingAnswer>(machine.advanceAfterCorrect())
        val secondWrong = second.choices.first { it != second.correctAnswer }
        assertIs<CombinedMappingState.Incorrect>(machine.submitAnswer(secondWrong))
        assertEquals(2, machine.errorCount)
        assertEquals(0, machine.correctCount)
    }

    @Test
    fun levelsTwoThroughSixExposeEveryPlannedStructure() {
        val factory = CombinedQuestionFactory(Random(20260904))

        (2..6).forEach { level ->
            repeat(20) {
                val question = factory.create(level)

                assertEquals(level, question.level)
                assertEquals(MappingForm.forLevel(level), question.form)
                assertTrue(question.sourceType != question.firstTargetType)
                assertTrue(question.correctAnswerFor(CombinedStep.FIRST) in question.structuralChoices)
                assertTrue(question.mappings.all { mapping ->
                    GuitarCore.mappingForDegree(mapping.degree) == mapping
                })
            }
        }
    }

    @Test
    fun orderedAndSetStructuresReuseSemanticAnswerValues() {
        val factory = CombinedQuestionFactory(Random(41))

        assertIs<AnswerValue.SymbolSequence>(factory.create(2).semanticAnswer)
        assertIs<AnswerValue.SymbolSequence>(factory.create(3).semanticAnswer)
        assertIs<AnswerValue.Choice>(factory.create(4).semanticAnswer)
        assertIs<AnswerValue.SymbolSequence>(factory.create(5).semanticAnswer)
        assertIs<AnswerValue.SymbolSet>(factory.create(6).semanticAnswer)
    }

    @Test
    fun missingQuestionsBlankOneItemAndAskForThatRepresentation() {
        val question = CombinedQuestionFactory(Random(92)).create(4)
        val correct = question.correctAnswerFor(CombinedStep.FIRST)

        assertEquals(1, question.promptFor(CombinedStep.FIRST).count { it == '?' })
        assertTrue(correct in AnswerOptions.forKind(question.sourceType))
        assertTrue(correct in question.structuralChoices)
        assertEquals(
            AnswerValue.Choice(correct),
            question.semanticAnswer,
        )
    }

    @Test
    fun aStructuralLevelCompletesInOneAnswerAndLevelSelectionIsExplicit() {
        val machine = CombinedMappingStateMachine(Random(73), initialLevel = 2)
        val pair = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)

        assertEquals(2, pair.question.level)
        val completed = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(pair.correctAnswer),
        )
        assertTrue(completed.completesQuestion)
        assertEquals(1, machine.correctCount)

        val chord = assertIs<CombinedMappingState.AwaitingAnswer>(machine.selectLevel(6))
        assertEquals(6, machine.selectedLevel)
        assertEquals(MappingForm.CHORD_SET, chord.question.form)
        assertIs<CombinedMappingState.Correct>(machine.submitAnswer(chord.correctAnswer))
        assertEquals(2, machine.correctCount)
    }

    @Test
    fun audioSingleUsesOnePitchAndCompletesInOneAnswer() {
        val machine = CombinedMappingStateMachine(
            random = Random(101),
            initialLevel = 1,
            initialAudioPromptsEnabled = true,
        )
        val awaiting = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)

        assertTrue(awaiting.question.usesAudioPrompt)
        assertEquals(1, awaiting.question.audioCue?.pitches?.size)
        val correct = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(awaiting.correctAnswer),
        )
        assertTrue(correct.completesQuestion)
        assertEquals(1, machine.correctCount)
    }

    @Test
    fun audioStructuresUseSequencesAndChordPlayback() {
        val factory = CombinedQuestionFactory(Random(102))

        listOf(2, 3, 5).forEach { level ->
            val question = factory.create(level, audioPrompt = true)
            assertTrue(question.usesAudioPrompt)
            assertEquals(PitchPlaybackStyle.SEQUENCE, question.audioCue?.style)
            assertEquals(question.mappings.size, question.audioCue?.pitches?.size)
        }
        val chord = factory.create(6, audioPrompt = true)
        assertEquals(PitchPlaybackStyle.CHORD, chord.audioCue?.style)
        assertEquals(chord.mappings.size, chord.audioCue?.pitches?.size)
    }

    @Test
    fun missingItemsRemainVisualAndDefaultQuestionsDoNotRegress() {
        val factory = CombinedQuestionFactory(Random(103))

        val missing = factory.create(4, audioPrompt = true)
        assertTrue(!missing.usesAudioPrompt)
        assertEquals(null, missing.audioCue)
        (1..6).forEach { level ->
            assertTrue(!factory.create(level).usesAudioPrompt)
        }
    }
}
