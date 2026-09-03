package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Progress
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
    fun noteSetUsesStableUnorderedTargetsAndKnowledgeId() {
        val targets = listOf(position(6, 0), position(4, 2), position(1, 0))
        val question = factory.createFretboardNoteSetQuestion("E", targets.reversed(), "LOW_POSITION")
        val answer = assertIs<AnswerValue.FretSet>(question.correctAnswerValue)

        assertEquals(AnswerMode.FRETBOARD_SET, question.answerMode)
        assertEquals("E", question.prompt)
        assertEquals(
            setOf(fret(6, 0), fret(4, 2), fret(1, 0)),
            answer.positions,
        )
        assertEquals(
            "FretToNoteSet:LOW_POSITION:E:s1f0-s4f2-s6f0",
            question.knowledgeItemId,
        )
    }

    @Test
    fun firstPositionStagesContainTheExpectedRealTargets() {
        assertTargets(maxFret = 0, note = "E", expected = setOf(6 to 0, 1 to 0))
        assertTargets(maxFret = 1, note = "F", expected = setOf(6 to 1, 1 to 1))
        assertTargets(maxFret = 1, note = "C", expected = setOf(2 to 1))
        assertTargets(maxFret = 2, note = "E", expected = setOf(6 to 0, 4 to 2, 1 to 0))
        assertTargets(maxFret = 3, note = "C", expected = setOf(5 to 3, 2 to 1))
        assertTargets(maxFret = 4, note = "B", expected = setOf(5 to 2, 3 to 4, 2 to 0))
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

        assertIs<QuestionState.SetProgress>(machine.submitAnswer(fret(1, 0)))
        val completed = assertIs<QuestionState.SetCompleted>(machine.submitAnswer(fret(6, 0)))
        assertTrue(completed.result.isCorrect)
        assertEquals(1, store.loadProgress(question.knowledgeItemId)?.attempts)
        assertEquals(1, store.loadSessions().last().questionCount)
    }

    @Test
    fun oneWrongClickRecordsOnceThenRequiresEveryRemainingCorrection() {
        val targets = listOf(position(6, 0), position(4, 2), position(1, 0))
        val question = factory.createFretboardNoteSetQuestion("E", targets, "LOW_POSITION")
        val store = InMemoryTrainingStore()
        val session = sessionFor(question, store)
        val machine = TrainingStateMachine(session)

        machine.submitAnswer(fret(4, 2))
        val required = assertIs<QuestionState.SetCorrectionRequired>(
            machine.submitAnswer(fret(5, 0)),
        )
        assertFalse(required.result.isCorrect)
        assertEquals(1, session.currentSession.questionCount)
        val recorded = store.loadProgress(question.knowledgeItemId)
        assertEquals(1, recorded?.attempts)

        assertSame(required, machine.submitAnswer(fret(5, 0)))
        val correcting = assertIs<QuestionState.SetCorrectionProgress>(
            machine.submitAnswer(fret(1, 0)),
        )
        assertEquals(setOf(fret(4, 2), fret(1, 0)), correcting.confirmedPositions)
        val confirmed = assertIs<QuestionState.SetCorrectionConfirmed>(
            machine.submitAnswer(fret(6, 0)),
        )
        assertEquals(targets.map { fret(it.string, it.fret) }.toSet(), confirmed.confirmedPositions)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(recorded, store.loadProgress(question.knowledgeItemId))
    }

    @Test
    fun firstTenNewUserQuestionsCoverEveryOpenStringNoteTwiceThenAdvance() {
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

        repeat(10) { index ->
            val awaiting = assertIs<QuestionState.AwaitingSetAnswer>(machine.state)
            prompts += awaiting.question.prompt
            assertTrue(awaiting.question.targetPositions.all { it.fret == 0 })
            val targets = assertIs<AnswerValue.FretSet>(awaiting.question.correctAnswerValue)
                .positions
            targets.forEach { machine.submitAnswer(it) }
            assertIs<QuestionState.SetCompleted>(machine.state)
            if (index < 9) machine.nextQuestion()
        }

        assertEquals(
            mapOf("E" to 2, "A" to 2, "D" to 2, "G" to 2, "B" to 2),
            prompts.groupingBy { it }.eachCount(),
        )
        assertEquals(1, session.currentSettings().firstPositionMaxFret)
        assertEquals(0, session.currentSettings().firstPositionStageAttempts)
        assertFalse(session.currentSettings().firstPositionComplete)
    }

    @Test
    fun stageCannotAdvanceWhileOneCurrentItemWeightIsStillHigh() {
        val questions = module.buildQuestionBank(
            NoteTrainingRange.LOW_POSITION.applyTo(Settings()),
        )
        val progress = questions.associate { question ->
            question.knowledgeItemId to Progress(
                knowledgeItemId = question.knowledgeItemId,
                attempts = 3,
                correct = 2,
                weight = if (question.prompt == "E") 1.6 else 1.0,
            )
        }

        assertFalse(FirstPositionGrowthRules.qualifies(10, questions, progress))
        assertTrue(
            FirstPositionGrowthRules.qualifies(
                10,
                questions,
                progress.mapValues { (_, value) -> value.copy(weight = 1.0) },
            ),
        )
    }

    @Test
    fun higherLevelsStaySuppressedUntilTheFullFirstPositionStageIsMastered() {
        val growing = NoteTrainingRange.LOW_POSITION.applyTo(
            Settings(
                unlockedFretboardLevel = 6,
                firstPositionMaxFret = 4,
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

    private fun assertTargets(maxFret: Int, note: String, expected: Set<Pair<Int, Int>>) {
        val settings = NoteTrainingRange.LOW_POSITION.applyTo(
            Settings(firstPositionMaxFret = maxFret),
        )
        val question = module.buildQuestionBank(settings).single { it.prompt == note }
        assertEquals(expected, question.targetPositions.map { it.string to it.fret }.toSet())
    }

    private fun sessionFor(
        question: Question,
        store: InMemoryTrainingStore,
    ): TrainingSession = TrainingSession(
        engine = TrainingEngine(
            settings = Settings(),
            random = Random(1),
            progressProvider = { store.loadProgress() },
            module = object : TrainingModule {
                override val id = TrainingModuleIds.FRET_NOTE
                override val title = "set-test"
                override fun buildQuestionBank(settings: Settings): List<Question> = listOf(question)
            },
        ),
        store = store,
    )

    private fun position(string: Int, fret: Int): FretPosition =
        GuitarCore.getFretPosition(string, fret)

    private fun fret(string: Int, fret: Int): AnswerValue.FretPosition =
        AnswerValue.FretPosition(string, fret)
}
