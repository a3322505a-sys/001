package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FretboardSetTest {
    private val factory = QuestionFactory()
    private val module = FirstFretboardModule()

    @Test
    fun noteSetSeparatesRequiredSetFromCorrectUniverse() {
        val question = module.buildQuestionBank(
            NoteTrainingRange.LOW_POSITION.applyTo(
                Settings(
                    firstPositionBaselineComplete = true,
                    firstPositionActiveKnowledgeIds = setOf("s6f1", "s2f1"),
                ),
            ),
        ).single { it.prompt == "C" }

        assertEquals(
            setOf(fret(2, 1)),
            assertIs<AnswerValue.FretSet>(question.correctAnswerValue).positions,
        )
        assertEquals(
            setOf(2 to 1, 5 to 3),
            question.correctUniversePositions.map { it.string to it.fret }.toSet(),
        )
    }

    @Test
    fun newFirstPositionStartsWithFiveOpenStringGroupsAndTheFullRealUniverse() {
        val questions = module.buildQuestionBank(NoteTrainingRange.LOW_POSITION.applyTo(Settings()))

        assertEquals(listOf("E", "A", "D", "G", "B"), questions.map { it.prompt })
        assertTrue(questions.all { it.targetPositions.all { target -> target.fret == 0 } })
        assertEquals(
            setOf(6 to 0, 1 to 0),
            questions.single { it.prompt == "E" }.targetPositions
                .map { it.string to it.fret }.toSet(),
        )
        assertEquals(
            setOf(6 to 0, 4 to 2, 1 to 0),
            questions.single { it.prompt == "E" }
                .correctUniversePositions.map { it.string to it.fret }.toSet(),
        )
    }

    @Test
    fun setAnswerRequiresEveryTargetAndIgnoresDuplicateConfirmedClicks() {
        val targets = listOf(position(6, 0), position(4, 2), position(1, 0))
        val question = factory.createFretboardNoteSetQuestion("E", targets, "LOW_POSITION")
        val store = InMemoryTrainingStore()
        val machine = TrainingStateMachine(sessionFor(question, store))

        val first = assertIs<QuestionState.SetProgress>(machine.submitAnswer(fret(4, 2)))
        assertEquals(setOf(fret(4, 2)), first.selectedPositions)
        assertEquals(0, store.loadSessions().last().questionCount)
        assertSame(first, machine.submitAnswer(fret(4, 2)))

        machine.submitAnswer(fret(1, 0))
        val completed = assertIs<QuestionState.SetCompleted>(machine.submitAnswer(fret(6, 0)))
        assertTrue(completed.result.isCorrect)
        assertEquals(1, store.loadProgress(question.knowledgeItemId)?.attempts)
    }

    @Test
    fun aheadCorrectPositionIsGreenProgressAndNeverRecordsAnError() {
        val question = factory.createFretboardNoteSetQuestion(
            note = "C",
            targets = listOf(position(2, 1)),
            rangeId = "LOW_POSITION",
            correctUniverse = listOf(position(2, 1), position(5, 3)),
        )
        val store = InMemoryTrainingStore()
        val machine = TrainingStateMachine(sessionFor(question, store))

        val ahead = assertIs<QuestionState.SetProgress>(machine.submitAnswer(fret(5, 3)))
        assertEquals(emptySet(), ahead.selectedPositions)
        assertEquals(setOf(fret(5, 3)), ahead.extraCorrectPositions)
        assertEquals(0, store.loadSessions().last().questionCount)
        assertEquals(null, store.loadProgress(question.knowledgeItemId))

        val completed = assertIs<QuestionState.SetCompleted>(machine.submitAnswer(fret(2, 1)))
        assertTrue(completed.result.isCorrect)
        assertEquals(setOf(fret(5, 3)), completed.extraCorrectPositions)
        assertEquals(1, store.loadSessions().last().questionCount)
    }

    @Test
    fun genuinelyWrongClickRecordsOnceAndRequiresCorrection() {
        val targets = listOf(position(6, 0), position(1, 0))
        val question = factory.createFretboardNoteSetQuestion(
            "E",
            targets,
            "LOW_POSITION",
            correctUniverse = targets + position(4, 2),
        )
        val store = InMemoryTrainingStore()
        val machine = TrainingStateMachine(sessionFor(question, store))

        val required = assertIs<QuestionState.SetCorrectionRequired>(
            machine.submitAnswer(fret(5, 0)),
        )
        assertFalse(required.result.isCorrect)
        assertSame(required, machine.submitAnswer(fret(5, 0)))
        assertIs<QuestionState.SetCorrectionProgress>(machine.submitAnswer(fret(1, 0)))
        assertIs<QuestionState.SetCorrectionConfirmed>(machine.submitAnswer(fret(6, 0)))
        assertEquals(1, store.loadProgress(question.knowledgeItemId)?.attempts)
    }

    @Test
    fun firstFiveQuestionsCoverEveryOpenStringGroupOnceThenActivateOneCoordinate() {
        val store = InMemoryTrainingStore()
        val settings = NoteTrainingRange.LOW_POSITION.applyTo(Settings())
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                random = Random(81),
                progressProvider = { store.loadProgress() },
                module = module,
            ),
            store = store,
        )
        val machine = TrainingStateMachine(session)
        val prompts = mutableListOf<String>()

        repeat(5) { index ->
            val awaiting = assertIs<QuestionState.AwaitingSetAnswer>(machine.state)
            prompts += awaiting.question.prompt
            assertTrue(awaiting.question.targetPositions.all { it.fret == 0 })
            assertIs<AnswerValue.FretSet>(awaiting.question.correctAnswerValue)
                .positions.forEach { machine.submitAnswer(it) }
            assertIs<QuestionState.SetCompleted>(machine.state)
            if (index < 4) machine.nextQuestion()
        }

        assertEquals(
            mapOf("E" to 1, "A" to 1, "D" to 1, "G" to 1, "B" to 1),
            prompts.groupingBy { it }.eachCount(),
        )
        assertTrue(session.currentSettings().firstPositionBaselineComplete)
        assertEquals(1, session.currentSettings().firstPositionActiveKnowledgeIds.size)
        assertFalse(session.currentSettings().firstPositionComplete)
    }

    @Test
    fun growthAddsExactlyOneCoordinateAfterTheCurrentNewItemSucceeds() {
        val first = FirstPositionCurriculum.expansionPositions.first()
        val settings = NoteTrainingRange.LOW_POSITION.applyTo(
            Settings(
                firstPositionBaselineComplete = true,
                firstPositionActiveKnowledgeIds = setOf(FirstPositionCurriculum.id(first)),
            ),
        )
        val store = InMemoryTrainingStore().also { it.saveSettings(settings) }
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                random = Random(2),
                progressProvider = { store.loadProgress() },
                module = module,
            ),
            store = store,
        )

        var question = session.currentQuestion()
        while (first !in question.targetPositions) question = session.nextQuestion()
        session.submitAnswer(question.correctAnswerValue)

        assertEquals(2, session.currentSettings().firstPositionActiveKnowledgeIds.size)
    }

    @Test
    fun higherLevelsStaySuppressedUntilFirstPositionKnowledgeIsComplete() {
        val growing = NoteTrainingRange.LOW_POSITION.applyTo(
            Settings(
                unlockedFretboardLevel = 6,
                firstPositionBaselineComplete = true,
                firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                    .map(FirstPositionCurriculum::id).toSet(),
                firstPositionComplete = false,
            ),
        )
        val completed = growing.copy(firstPositionComplete = true)

        assertEquals(setOf(1), module.buildQuestionBank(growing).map { it.curriculumLevel }.toSet())
        assertEquals(
            (1..6).toSet(),
            module.buildQuestionBank(completed).map { it.curriculumLevel }.toSet(),
        )
    }

    private fun sessionFor(question: Question, store: InMemoryTrainingStore): TrainingSession =
        TrainingSession(
            engine = TrainingEngine(
                settings = Settings(),
                random = Random(1),
                progressProvider = { store.loadProgress() },
                module = object : TrainingModule {
                    override val id = TrainingModuleIds.FRET_NOTE
                    override val title = "set-test"
                    override fun buildQuestionBank(settings: Settings): List<Question> =
                        listOf(question)
                },
            ),
            store = store,
        )

    private fun position(string: Int, fret: Int): FretPosition =
        GuitarCore.getFretPosition(string, fret)

    private fun fret(string: Int, fret: Int): AnswerValue.FretPosition =
        AnswerValue.FretPosition(string, fret)
}
