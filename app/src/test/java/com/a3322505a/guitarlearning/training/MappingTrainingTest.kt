package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MappingTrainingTest {
    private val directions = listOf(
        QuestionType.NoteToSolfege,
        QuestionType.SolfegeToNote,
        QuestionType.NoteToDegree,
        QuestionType.DegreeToNote,
        QuestionType.SolfegeToDegree,
        QuestionType.DegreeToSolfege,
    )

    @Test
    fun sevenCanonicalTriplesAreOrderedAndMutuallyAddressable() {
        val expected = listOf(
            "C" to ("Do" to 1),
            "D" to ("Re" to 2),
            "E" to ("Mi" to 3),
            "F" to ("Fa" to 4),
            "G" to ("Sol" to 5),
            "A" to ("La" to 6),
            "B" to ("Si" to 7),
        )

        assertEquals(
            expected,
            GuitarCore.fixedMappings.map { it.note to (it.solfege to it.degree) },
        )
        GuitarCore.fixedMappings.forEach { mapping ->
            assertEquals(mapping, GuitarCore.mappingForNote(mapping.note))
            assertEquals(mapping, GuitarCore.mappingForDegree(mapping.degree))
            assertEquals(mapping.solfege, GuitarCore.solfegeFor(mapping.note))
            assertEquals(mapping.degree, GuitarCore.degreeFor(mapping.note))
        }
    }

    @Test
    fun allSixDirectionsUseTheSameTripleAndSevenChoiceDomain() {
        val factory = QuestionFactory()

        GuitarCore.fixedMappings.forEach { mapping ->
            directions.forEach { type ->
                val question = when (type) {
                    QuestionType.DegreeToNote,
                    QuestionType.DegreeToSolfege ->
                        factory.createForDegree(type, mapping.degree)
                    else -> factory.createForNote(type, mapping.note)
                }

                assertEquals(type, question.type)
                assertNull(question.fretPosition)
                assertEquals(mapping.note, question.note)
                assertEquals(mapping.solfege, question.solfege)
                assertEquals(mapping.degree, question.degree)
                assertEquals(7, question.choices.size)
                assertEquals(1, question.choices.count { it == question.correctAnswer })
                assertEquals(expectedAnswer(type, mapping.note, mapping.solfege, mapping.degree), question.correctAnswer)
            }
        }
    }

    @Test
    fun engineCanGenerateEveryDirectionWithAllSevenLegalAnswers() {
        val engine = TrainingEngine(
            random = kotlin.random.Random(41),
            enabledQuestionTypes = directions,
        )

        directions.forEach { type ->
            repeat(7) {
                val question = engine.generateQuestion(type)
                assertEquals(type, question.type)
                assertEquals(7, question.choices.size)
                assertTrue(question.correctAnswer in question.choices)
                assertNull(question.fretPosition)
            }
        }
    }

    private fun expectedAnswer(type: QuestionType, note: String, solfege: String, degree: Int): String =
        when (type) {
            QuestionType.NoteToSolfege -> solfege
            QuestionType.SolfegeToNote -> note
            QuestionType.NoteToDegree,
            QuestionType.SolfegeToDegree -> degree.toString()
            QuestionType.DegreeToNote -> note
            QuestionType.DegreeToSolfege -> solfege
            else -> error("Not a mapping direction")
        }
}
