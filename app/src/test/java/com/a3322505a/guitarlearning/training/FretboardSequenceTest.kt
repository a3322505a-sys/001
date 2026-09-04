package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FretboardSequenceTest {
    private val factory = QuestionFactory()

    @Test
    fun everyTrainingRangeKeepsItsSequencesInsideItsCoordinates() {
        val module = FirstFretboardModule()

        NoteTrainingRange.entries.forEach { range ->
            val settings = range.applyTo(
                Settings(
                    unlockedFretboardLevel = 6,
                    firstPositionBaselineComplete = true,
                    firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                        .map(FirstPositionCurriculum::id).toSet(),
                    firstPositionComplete = true,
                ),
            )
            val questions = module.buildQuestionBank(settings)
            val expectedLevels = if (range == NoteTrainingRange.MID_POSITION) {
                (1..5).toSet()
            } else {
                (1..6).toSet()
            }
            assertEquals(expectedLevels, questions.map { it.curriculumLevel }.toSet(), range.name)

            questions.filter { it.answerMode == AnswerMode.FRETBOARD_SEQUENCE }.forEach { question ->
                val notes = question.targetPositions.map { it.note }
                if (question.kind == "c_major_ascending_fragment") {
                    assertEquals(listOf("C", "D", "E"), notes)
                }
                if (question.kind == "c_major_scale_ascending") {
                    assertEquals(listOf("C", "D", "E", "F", "G", "A", "B", "C"), notes)
                }
                if (question.kind == "c_major_scale_descending") {
                    assertEquals(listOf("C", "B", "A", "G", "F", "E", "D", "C"), notes)
                }
                val chordNotes = mapOf(
                    "c_major_triad" to listOf("C", "E", "G"),
                    "g_major_triad" to listOf("G", "B", "D"),
                    "a_minor_triad" to listOf("A", "C", "E"),
                    "e_minor_triad" to listOf("E", "G", "B"),
                    "f_major_triad" to listOf("F", "A", "C"),
                )[question.kind]
                if (chordNotes != null) assertEquals(chordNotes, notes)
                assertTrue(question.targetPositions.all { it.string in settings.selectedStrings })
                assertTrue(question.targetPositions.all { it.fret in settings.fretStart..settings.fretEnd })
            }
        }
    }

    @Test
    fun levelSixContainsAllFiveNaturalChordToneSequencesInFirstPosition() {
        val settings = Settings(
            unlockedFretboardLevel = 6,
            firstPositionBaselineComplete = true,
            firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                .map(FirstPositionCurriculum::id).toSet(),
            firstPositionComplete = true,
        )
        val chordQuestions = FirstFretboardModule().buildQuestionBank(settings)
            .filter { it.kind.endsWith("_triad") }
            .groupBy { it.kind }

        assertEquals(
            setOf(
                "c_major_triad",
                "g_major_triad",
                "a_minor_triad",
                "e_minor_triad",
                "f_major_triad",
            ),
            chordQuestions.keys,
        )
        assertTrue(chordQuestions.values.flatten().all { question ->
            question.answerMode == AnswerMode.FRETBOARD_SEQUENCE &&
                question.targetPositions.size == 3 &&
                question.targetPositions.all { it.fret in 0..4 && GuitarCore.isNaturalNote(it.note) }
        })
    }

    @Test
    fun fullCMajorScaleUsesOneFixedFirstPositionRouteAndItsExactReverse() {
        val settings = Settings(
            unlockedFretboardLevel = 5,
            noteTrainingRangeId = NoteTrainingRange.FULL_FRETBOARD.name,
            fretStart = 0,
            fretEnd = 12,
            firstPositionBaselineComplete = true,
            firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                .map(FirstPositionCurriculum::id).toSet(),
            firstPositionComplete = true,
        )
        val questions = FirstFretboardModule().buildQuestionBank(settings)
        val ascending = questions.single { it.kind == "c_major_scale_ascending" }
        val descending = questions.single { it.kind == "c_major_scale_descending" }

        assertEquals(FirstPositionScaleRoutes.cMajorAscending, ascending.targetPositions)
        assertEquals(ascending.targetPositions.reversed(), descending.targetPositions)
        assertEquals(
            listOf("C", "D", "E", "F", "G", "A", "B", "C"),
            ascending.targetPositions.map { it.note },
        )
        assertTrue(ascending.targetPositions.all { it.fret in 0..4 })
        assertTrue(ascending.targetPositions.all { GuitarCore.isNaturalNote(it.note) })
        assertTrue(ascending.knowledgeItemId.contains("ascending"))
        assertTrue(descending.knowledgeItemId.contains("descending"))
    }

    @Test
    fun correctSequenceRecordsOnlyTheCompletedQuestion() {
        val store = InMemoryTrainingStore()
        val question = sequenceQuestion()
        val session = sequenceSession(question, store)
        val machine = TrainingStateMachine(session)
        val correct = assertIs<AnswerValue.FretSequence>(question.correctAnswerValue).positions

        assertIs<QuestionState.AwaitingSequenceAnswer>(machine.state)
        assertEquals(listOf(correct[0]), assertIs<QuestionState.SequenceProgress>(
            machine.submitAnswer(correct[0]),
        ).selectedPositions)
        assertEquals(0, session.currentSession.questionCount)
        assertEquals(correct.take(2), assertIs<QuestionState.SequenceProgress>(
            machine.submitAnswer(correct[1]),
        ).selectedPositions)
        assertEquals(0, session.currentSession.questionCount)

        val completed = assertIs<QuestionState.SequenceCompleted>(
            machine.submitAnswer(correct[2]),
        )
        assertTrue(completed.result.isCorrect)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(1, session.currentSession.correctCount)
        assertEquals(1, store.loadProgress(question.knowledgeItemId)?.attempts)
        assertEquals(1, store.loadLevelProgress(6)?.attempts)
    }

    @Test
    fun sequenceErrorRequiresCorrectionWithoutAddingAnotherAttempt() {
        val store = InMemoryTrainingStore()
        val question = sequenceQuestion()
        val session = sequenceSession(question, store)
        val machine = TrainingStateMachine(session)
        val correct = assertIs<AnswerValue.FretSequence>(question.correctAnswerValue).positions
        val wrong = AnswerValue.FretPosition(6, 1)

        machine.submitAnswer(correct[0])
        val correction = assertIs<QuestionState.CorrectionRequired>(machine.submitAnswer(wrong))
        assertEquals(listOf(correct[0]), correction.confirmedPositions)
        assertEquals(correct[1], correction.correctPosition)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(0, session.currentSession.correctCount)

        assertSame(correction, machine.submitAnswer(wrong))
        val confirmed = assertIs<QuestionState.CorrectionConfirmed>(
            machine.submitAnswer(correct[1]),
        )
        assertEquals(listOf(correct[0]), confirmed.confirmedPositions)
        assertEquals(1, store.loadProgress(question.knowledgeItemId)?.attempts)
        assertIs<QuestionState.AwaitingSequenceAnswer>(machine.nextQuestion())
    }

    @Test
    fun finalMixRatiosAreNormalizedAndObservedAtLevelSix() {
        (1..6).forEach { level ->
            val weights = FretboardMixingWeights.forUnlockedLevel(level)
            assertEquals((1..level).toSet(), weights.keys)
            assertEquals(1.0, weights.values.sum(), absoluteTolerance = 0.000_001)
        }
        assertEquals(mapOf(1 to 0.70, 2 to 0.30), FretboardMixingWeights.forUnlockedLevel(2))
        assertEquals(
            mapOf(1 to 0.50, 2 to 0.30, 3 to 0.20),
            FretboardMixingWeights.forUnlockedLevel(3),
        )

        val engine = TrainingEngine(
            settings = Settings(
                unlockedFretboardLevel = 6,
                firstPositionBaselineComplete = true,
                firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                    .map(FirstPositionCurriculum::id).toSet(),
                firstPositionComplete = true,
            ),
            random = Random(73),
            module = FirstFretboardModule(),
        )
        val counts = mutableMapOf<Int, Int>()
        repeat(12_000) {
            val level = engine.generateQuestion().curriculumLevel
            counts[level] = counts.getOrDefault(level, 0) + 1
        }
        FretboardMixingWeights.forUnlockedLevel(6).forEach { (level, expected) ->
            val observed = counts.getValue(level) / 12_000.0
            assertTrue(abs(observed - expected) < 0.03, "Lv.$level: $observed vs $expected")
        }
    }

    private fun sequenceQuestion(): Question = factory.createSequenceCurriculumQuestion(
        level = 6,
        kind = "c_major_triad",
        prompt = "C→E→G",
        targets = listOf(
            GuitarCore.getFretPosition(5, 3),
            GuitarCore.getFretPosition(4, 2),
            GuitarCore.getFretPosition(3, 0),
        ),
        relationId = "test",
    )

    private fun sequenceSession(
        question: Question,
        store: InMemoryTrainingStore,
    ): TrainingSession = TrainingSession(
        engine = TrainingEngine(
            settings = Settings(unlockedFretboardLevel = 6),
            random = Random(1),
            progressProvider = { store.loadProgress() },
            module = object : TrainingModule {
                override val id = TrainingModuleIds.FRET_NOTE
                override val title = "sequence-test"
                override fun buildQuestionBank(settings: Settings): List<Question> = listOf(question)
            },
        ),
        store = store,
    )
}
