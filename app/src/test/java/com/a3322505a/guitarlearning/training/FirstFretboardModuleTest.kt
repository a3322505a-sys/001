package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.LevelProgress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FirstFretboardModuleTest {
    private val module = FirstFretboardModule()

    @Test
    fun defaultBankContainsOnlyLevelOneNoteSets() {
        val questions = module.buildQuestionBank(Settings())

        assertTrue(questions.all { it.curriculumLevel == 1 })
        assertTrue(questions.all { it.answerMode == AnswerMode.FRETBOARD_SET })
        assertTrue(questions.all { it.knowledgeItemId.startsWith("FretToNoteSet:") })
    }

    @Test
    fun fullyUnlockedBankContainsEverySingleTargetLevel() {
        NoteTrainingRange.entries.forEach { range ->
            val questions = module.buildQuestionBank(
                range.applyTo(
                    Settings(
                        unlockedFretboardLevel = 5,
                        firstPositionBaselineComplete = true,
                        firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                            .map(FirstPositionCurriculum::id).toSet(),
                        firstPositionComplete = true,
                    ),
                ),
            )

            assertEquals((1..5).toSet(), questions.map { it.curriculumLevel }.toSet(), range.name)
            assertTrue(
                questions.all {
                    it.answerMode == AnswerMode.FRETBOARD ||
                        it.answerMode == AnswerMode.FRETBOARD_SET ||
                        it.answerMode == AnswerMode.FRETBOARD_SEQUENCE
                },
            )
        }
    }

    @Test
    fun octaveQuestionsCarryAnAnchorAndAnExactHigherOctaveTarget() {
        val questions = module.buildQuestionBank(completedSettings(2))
            .filter { it.curriculumLevel == 2 }

        assertTrue(questions.isNotEmpty())
        questions.forEach { question ->
            val payload = assertIs<FretboardCurriculumPayload>(question.payload)
            val anchor = requireNotNull(payload.anchor)
            assertEquals(anchor.note, payload.target.note)
            assertEquals(12, pitch(payload.target) - pitch(anchor))
        }
    }

    @Test
    fun wholeHalfAndIntervalQuestionsUseTheRequestedPitchDistance() {
        val questions = module.buildQuestionBank(completedSettings(4))
            .filter { it.curriculumLevel in 3..4 }

        assertTrue(questions.isNotEmpty())
        questions.forEach { question ->
            val payload = assertIs<FretboardCurriculumPayload>(question.payload)
            val anchor = requireNotNull(payload.anchor)
            val expected = when (payload.relationId.substringBefore(":")) {
                "half_step", "minor_second" -> 1
                "whole_step", "major_second" -> 2
                "minor_third" -> 3
                "major_third" -> 4
                "perfect_fourth" -> 5
                "perfect_fifth" -> 7
                else -> error("Unexpected relation ${payload.relationId}")
            }
            assertEquals(expected, pitch(payload.target) - pitch(anchor))
        }
    }

    @Test
    fun cMajorDegreeQuestionsPointToTheDisplayedDegree() {
        val questions = module.buildQuestionBank(completedSettings(5))
            .filter { it.curriculumLevel == 5 && it.kind == "c_major_degree" }

        questions.forEach { question ->
            val payload = assertIs<FretboardCurriculumPayload>(question.payload)
            val degree = requireNotNull(
                com.a3322505a.guitarlearning.core.GuitarCore.degreeFor(payload.target.note),
            )
            assertTrue(question.prompt.endsWith("${degree}级"))
        }
    }

    @Test
    fun eighteenCorrectInTwentyPermanentlyUnlocksTheNextLevel() {
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = Settings(),
                random = Random(8),
                progressProvider = { store.loadProgress() },
                module = module,
            ),
            store = store,
        )

        repeat(20) { index ->
            val question = session.currentQuestion()
            val correct = assertIs<AnswerValue.FretSet>(question.correctAnswerValue)
            val answer = if (index < 18) correct else wrongSet(correct)
            session.submitAnswer(answer)
            if (index < 19) session.nextQuestion()
        }

        assertEquals(2, session.currentSettings().unlockedFretboardLevel)
        assertEquals(2, store.loadSettings().unlockedFretboardLevel)
        assertEquals(20, store.loadLevelProgress(1)?.attempts)
        assertEquals(18, store.loadLevelProgress(1)?.correct)
    }

    @Test
    fun fewerThanEighteenCorrectDoesNotUnlock() {
        val progress = LevelProgress(
            level = 1,
            recentResults = List(17) { true } + List(3) { false },
        )

        assertTrue(!FretboardLevelRules.qualifies(progress))
    }

    private fun wrongSet(correct: AnswerValue.FretSet): AnswerValue.FretSet {
        val first = correct.positions.first()
        return AnswerValue.FretSet(
            correct.positions - first + first.copy(
                fret = if (first.fret == 12) 11 else first.fret + 1,
            ),
        )
    }

    private fun completedSettings(level: Int): Settings = Settings(
        unlockedFretboardLevel = level,
        firstPositionBaselineComplete = true,
        firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
            .map(FirstPositionCurriculum::id).toSet(),
        firstPositionComplete = true,
    )

    private fun pitch(position: com.a3322505a.guitarlearning.core.FretPosition): Int =
        mapOf(6 to 40, 5 to 45, 4 to 50, 3 to 55, 2 to 59, 1 to 64)
            .getValue(position.string) + position.fret
}
