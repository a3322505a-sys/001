package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrainingEngineTest {
    @Test
    fun everyQuestionTypeGeneratesOneHundredNaturalQuestions() {
        val engine = TrainingEngine(random = Random(7))

        QuestionType.entries.forEach { type ->
            repeat(100) {
                val question = engine.generateQuestion(type)

                assertEquals(type, question.type)
                assertTrue(question.correctAnswer in question.choices)
                assertFalse(question.correctAnswer.contains("#"))
                assertTrue(question.knowledgeItemId.startsWith(type.name))
                if (type == QuestionType.FretToNote || type == QuestionType.FretToSolfege) {
                    val position = assertNotNull(question.fretPosition)
                    assertTrue(GuitarCore.isNaturalNote(position.note))
                    assertEquals(position.note, GuitarCore.getNote(position.string, position.fret))
                } else {
                    assertEquals(null, question.fretPosition)
                }
            }
        }
    }

    @Test
    fun fourDirectionsUseTheirOwnAnswersAndStableKnowledgeIds() {
        val factory = QuestionFactory()
        val position = GuitarCore.getFretPosition(6, 5)

        val fretToNote = factory.create(QuestionType.FretToNote, position)
        val fretToSolfege = factory.create(QuestionType.FretToSolfege, position)
        val noteToSolfege = factory.create(QuestionType.NoteToSolfege, position)
        val solfegeToNote = factory.create(QuestionType.SolfegeToNote, position)

        assertEquals("A", fretToNote.correctAnswer)
        assertEquals("La", fretToSolfege.correctAnswer)
        assertEquals("La", noteToSolfege.correctAnswer)
        assertEquals("A", solfegeToNote.correctAnswer)
        assertEquals(position, fretToNote.fretPosition)
        assertEquals(position, fretToSolfege.fretPosition)
        assertEquals(null, noteToSolfege.fretPosition)
        assertEquals(null, solfegeToNote.fretPosition)
        assertEquals(4, setOf(
            fretToNote.knowledgeItemId,
            fretToSolfege.knowledgeItemId,
            noteToSolfege.knowledgeItemId,
            solfegeToNote.knowledgeItemId,
        ).size)
        assertEquals(
            noteToSolfege.knowledgeItemId,
            factory.createForNote(QuestionType.NoteToSolfege, "A").knowledgeItemId,
        )
    }

    @Test
    fun submitAcceptsOneAnswerAndRejectsDuplicateSubmission() {
        val engine = TrainingEngine(random = Random(1))
        val question = engine.generateQuestion(QuestionType.FretToNote)

        val first = engine.submitAnswer(question.correctAnswer)
        val duplicate = engine.submitAnswer(question.choices.first { it != question.correctAnswer })

        assertTrue(first.accepted)
        assertTrue(first.isCorrect)
        assertFalse(duplicate.accepted)
        assertTrue(duplicate.isCorrect)
    }

    @Test
    fun invalidAnswerDoesNotConsumeTheQuestion() {
        val engine = TrainingEngine()
        val question = engine.generateQuestion(QuestionType.NoteToSolfege)

        val invalid = engine.submitAnswer("C#")
        val valid = engine.submitAnswer(question.correctAnswer)

        assertFalse(invalid.accepted)
        assertTrue(valid.accepted)
    }

    @Test
    fun oneHundredQuestionSessionHasNoDuplicateOrStateLeak() {
        val engine = TrainingEngine(random = Random(19))
        var correctCount = 0
        var incorrectCount = 0
        var question = engine.generateQuestion()

        repeat(100) { index ->
            val answer = if (index % 2 == 0) {
                question.correctAnswer
            } else {
                question.choices.first { it != question.correctAnswer }
            }
            val result = engine.submitAnswer(answer)
            val duplicate = engine.submitAnswer(question.correctAnswer)

            assertTrue(result.accepted)
            assertEquals(question.knowledgeItemId, result.knowledgeItemId)
            assertFalse(duplicate.accepted)
            if (result.isCorrect) correctCount++ else incorrectCount++
            if (index < 99) question = engine.nextQuestion()
        }

        assertEquals(100, correctCount + incorrectCount)
        assertEquals(50, correctCount)
        assertEquals(50, incorrectCount)
    }

    @Test
    fun mappingEngineRandomizesOnlyTheTwoIndependentDirections() {
        val engine = TrainingEngine(
            random = Random(23),
            enabledQuestionTypes = listOf(
                QuestionType.NoteToSolfege,
                QuestionType.SolfegeToNote,
            ),
        )

        repeat(100) {
            val question = engine.generateQuestion()

            assertTrue(
                question.type == QuestionType.NoteToSolfege ||
                    question.type == QuestionType.SolfegeToNote,
            )
            assertEquals(null, question.fretPosition)
            assertEquals(GuitarCore.solfegeFor(question.note), question.solfege)
            if (question.type == QuestionType.NoteToSolfege) {
                assertEquals(question.solfege, question.correctAnswer)
                assertEquals(AnswerOptions.solfege, question.choices)
            } else {
                assertEquals(question.note, question.correctAnswer)
                assertEquals(AnswerOptions.notes, question.choices)
            }
        }
    }

    @Test
    fun mappingQuestionAcceptsCorrectAndRejectsAnIncorrectChoice() {
        val engine = TrainingEngine(
            random = Random(29),
            enabledQuestionTypes = listOf(QuestionType.NoteToSolfege),
        )
        val question = engine.generateQuestion()
        val wrongAnswer = question.choices.first { it != question.correctAnswer }

        val wrong = engine.submitAnswer(wrongAnswer)

        assertTrue(wrong.accepted)
        assertFalse(wrong.isCorrect)
        assertEquals(question.correctAnswer, wrong.correctAnswer)
    }

    @Test
    fun updatingSettingsRegeneratesQuestionsInsideTheNewStringRange() {
        val engine = TrainingEngine(
            settings = com.a3322505a.guitarlearning.storage.Settings(
                selectedStrings = (1..6).toSet(),
            ),
            random = Random(31),
            enabledQuestionTypes = listOf(QuestionType.FretToNote),
        )

        engine.generateQuestion()
        engine.updateSettings(
            com.a3322505a.guitarlearning.storage.Settings(
                selectedStrings = StringDifficulty.CROSS_STRING.selectedStrings,
            ),
        )

        repeat(100) {
            val question = engine.nextQuestion()
            assertEquals(
                StringDifficulty.CROSS_STRING.selectedStrings,
                engine.settings().selectedStrings,
            )
            assertTrue(
                question.fretPosition?.string?.let {
                    it in StringDifficulty.CROSS_STRING.selectedStrings
                } == true,
            )
        }
    }
}
