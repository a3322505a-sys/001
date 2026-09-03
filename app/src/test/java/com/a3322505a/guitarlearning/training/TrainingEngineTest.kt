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
                assertTrue(question.knowledgeItemId.startsWith(type.name))
                if (type == QuestionType.FretToNote || type == QuestionType.FretToSolfege) {
                    val position = assertNotNull(question.fretPosition)
                    assertTrue(GuitarCore.isNaturalNote(position.note))
                    assertEquals(position.note, GuitarCore.getNote(position.string, position.fret))
                    if (type == QuestionType.FretToNote) {
                        assertEquals(AnswerMode.FRETBOARD, question.answerMode)
                        assertTrue(question.choices.isEmpty())
                        assertEquals(
                            AnswerValue.FretPosition(position.string, position.fret),
                            question.correctAnswerValue,
                        )
                    } else {
                        assertTrue(question.correctAnswer in question.choices)
                        assertFalse(question.correctAnswer.contains("#"))
                    }
                } else {
                    assertEquals(null, question.fretPosition)
                    assertTrue(question.correctAnswer in question.choices)
                    assertFalse(question.correctAnswer.contains("#"))
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

        assertEquals("6弦5品", fretToNote.correctAnswer)
        assertEquals("6弦 · A", fretToNote.prompt)
        assertEquals(AnswerValue.FretPosition(6, 5), fretToNote.correctAnswerValue)
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

        val first = engine.submitAnswer(question.correctAnswerValue)
        val duplicate = engine.submitAnswer(wrongAnswerFor(question))

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
                question.correctAnswerValue
            } else {
                wrongAnswerFor(question)
            }
            val result = engine.submitAnswer(answer)
            val duplicate = engine.submitAnswer(question.correctAnswerValue)

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
    fun updatingSettingsRegeneratesQuestionsInsideTheNewStringAndFretRange() {
        val engine = TrainingEngine(
            settings = com.a3322505a.guitarlearning.storage.Settings(
                selectedStrings = (1..6).toSet(),
            ),
            random = Random(31),
            enabledQuestionTypes = listOf(QuestionType.FretToNote),
        )

        engine.generateQuestion()
        val range = NoteTrainingRange.MID_POSITION
        engine.updateSettings(range.applyTo(engine.settings()))

        repeat(100) {
            val question = engine.nextQuestion()
            assertEquals(
                range.selectedStrings,
                engine.settings().selectedStrings,
            )
            assertTrue(
                question.fretPosition?.string?.let {
                    it in range.selectedStrings
                } == true,
            )
            assertTrue(question.fretPosition?.fret?.let { it in range.fretRange } == true)
        }
    }

    @Test
    fun fullFretboardDuplicateNotesKeepExactTargetsAndShowARegionHint() {
        val factory = QuestionFactory()
        val open = factory.create(
            QuestionType.FretToNote,
            GuitarCore.getFretPosition(1, 0),
            disambiguateOctave = true,
        )
        val octave = factory.create(
            QuestionType.FretToNote,
            GuitarCore.getFretPosition(1, 12),
            disambiguateOctave = true,
        )

        assertEquals(open.note, octave.note)
        assertEquals("1弦 · E · 0品区域", open.prompt)
        assertEquals("1弦 · E · 12品区域", octave.prompt)
        assertTrue(open.correctAnswerValue != octave.correctAnswerValue)
        assertTrue(open.knowledgeItemId != octave.knowledgeItemId)
    }

    private fun wrongAnswerFor(question: Question): AnswerValue = when (question.answerMode) {
        AnswerMode.CHOICE -> AnswerValue.Choice(
            question.answerChoices.first { it.id != question.correctChoiceId }.id,
        )
        AnswerMode.FRETBOARD -> {
            val correct = question.correctAnswerValue as AnswerValue.FretPosition
            AnswerValue.FretPosition(
                correct.string,
                if (correct.fret == 12) 11 else correct.fret + 1,
            )
        }
        AnswerMode.FRETBOARD_SET -> {
            val correct = question.correctAnswerValue as AnswerValue.FretSet
            val first = correct.positions.first()
            AnswerValue.FretSet(
                correct.positions - first + first.copy(
                    fret = if (first.fret == 12) 11 else first.fret + 1,
                ),
            )
        }
        AnswerMode.FRETBOARD_SEQUENCE -> {
            val correct = question.correctAnswerValue as AnswerValue.FretSequence
            val first = correct.positions.first()
            AnswerValue.FretSequence(
                listOf(
                    first.copy(fret = if (first.fret == 12) 11 else first.fret + 1),
                    *correct.positions.drop(1).toTypedArray(),
                ),
            )
        }
    }
}
