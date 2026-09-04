package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class NoteTrainingRangeRegressionTest {
    @Test
    fun lowPositionResetGeneratesAFirstQuestion() {
        assertResetGeneratesVisibleQuestion(NoteTrainingRange.LOW_POSITION)
    }

    @Test
    fun midPositionResetGeneratesAFirstQuestion() {
        assertResetGeneratesVisibleQuestion(NoteTrainingRange.MID_POSITION)
    }

    @Test
    fun fullFretboardResetGeneratesAFirstQuestion() {
        assertResetGeneratesVisibleQuestion(NoteTrainingRange.FULL_FRETBOARD)
    }

    @Test
    fun generatedQuestionsStayInsideEachSelectedRange() {
        NoteTrainingRange.entries.forEachIndexed { index, range ->
            val settings = range.applyTo(completedCurriculumSettings())
            val module = FirstFretboardModule()
            module.buildQuestionBank(settings).forEach { question ->
                assertQuestionInsideRange(question, range)
            }

            val engine = TrainingEngine(
                settings = settings,
                random = Random(410 + index),
                module = module,
            )
            repeat(120) {
                assertQuestionInsideRange(engine.generateQuestion(), range)
            }
        }
    }

    private fun assertResetGeneratesVisibleQuestion(range: NoteTrainingRange) {
        val settings = Settings()
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                random = Random(400 + range.ordinal),
                module = FirstFretboardModule(),
            ),
            store = InMemoryTrainingStore(),
        )
        val state = TrainingStateMachine(session).resetNoteTrainingRange(range)

        assertQuestionInsideRange(state.question, range)
    }

    private fun assertQuestionInsideRange(question: Question, range: NoteTrainingRange) {
        val positions = question.targetPositions +
            question.correctUniversePositions +
            listOfNotNull(question.anchorPosition)
        assertTrue(positions.isNotEmpty(), "Every fretboard question must expose a target")
        positions.forEach { position ->
            assertTrue(
                position.string in range.selectedStrings && position.fret in range.fretRange,
                "${question.kind} contains ${position.string}弦${position.fret}品 outside ${range.name}",
            )
        }
    }

    private fun completedCurriculumSettings(): Settings = Settings(
        unlockedFretboardLevel = 6,
        firstPositionBaselineComplete = true,
        firstPositionComplete = true,
        unlockedChordShapeCount = 5,
    )
}
