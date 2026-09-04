package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random

const val TAB_GUIDE_QUESTION_COUNT = 6

enum class TabExercise(
    val label: String,
) {
    SHORT_PHRASE("3–4 音短句"),
    ONE_MEASURE("一小节"),
}

data class TabQuestion(
    val id: Int,
    val targets: List<FretPosition>,
    val isGuide: Boolean,
    val exercise: TabExercise = TabExercise.SHORT_PHRASE,
) {
    init {
        require(targets.isNotEmpty()) { "A TAB question needs at least one target" }
        require(targets.all { it.string in 1..6 && it.fret in 0..4 }) {
            "TAB targets must stay inside the first position"
        }
        if (isGuide) {
            require(targets.size == 1) { "A guide question must contain one target" }
        } else when (exercise) {
            TabExercise.SHORT_PHRASE -> require(targets.size in 3..4) {
                "A short TAB phrase must contain three or four targets"
            }
            TabExercise.ONE_MEASURE -> require(targets.size in 6..8) {
                "A TAB measure must contain six to eight targets"
            }
        }
    }
}

sealed interface TabTrainingState {
    val question: TabQuestion

    data class Awaiting(
        override val question: TabQuestion,
        val selected: List<FretPosition> = emptyList(),
    ) : TabTrainingState

    data class CorrectionRequired(
        override val question: TabQuestion,
        val selected: List<FretPosition>,
        val wrong: FretPosition,
        val expected: FretPosition,
    ) : TabTrainingState

    data class CorrectionConfirmed(
        override val question: TabQuestion,
        val selected: List<FretPosition>,
        val wrong: FretPosition,
        val expected: FretPosition,
    ) : TabTrainingState

    data class Completed(
        override val question: TabQuestion,
        val selected: List<FretPosition>,
    ) : TabTrainingState
}

class TabTrainingStateMachine(
    guideCompleted: Boolean,
    private val random: Random = Random.Default,
    private val guideQuestionCount: Int = TAB_GUIDE_QUESTION_COUNT,
    private val onGuideCompleted: () -> Unit = {},
    initialExercise: TabExercise = TabExercise.SHORT_PHRASE,
) {
    private var remainingGuideQuestions = if (guideCompleted) 0 else guideQuestionCount
    private var nextQuestionId = 1
    private var formalQuestionCount = 0
    private var guideCompletionRecorded = guideCompleted

    var selectedExercise: TabExercise = initialExercise
        private set

    var state: TabTrainingState = TabTrainingState.Awaiting(createQuestion())
        private set

    init {
        require(guideQuestionCount in 5..10) { "The TAB guide must contain 5 to 10 questions" }
    }

    fun submit(position: FretPosition): TabTrainingState {
        state = when (val current = state) {
            is TabTrainingState.Awaiting -> submitAwaiting(current, position)
            is TabTrainingState.CorrectionRequired -> {
                if (position == current.expected) {
                    TabTrainingState.CorrectionConfirmed(
                        question = current.question,
                        selected = current.selected,
                        wrong = current.wrong,
                        expected = current.expected,
                    )
                } else {
                    current
                }
            }
            is TabTrainingState.CorrectionConfirmed,
            is TabTrainingState.Completed -> current
        }
        return state
    }

    fun nextQuestion(): TabTrainingState {
        check(
            state is TabTrainingState.Completed ||
                state is TabTrainingState.CorrectionConfirmed,
        ) { "The current TAB question must be completed before advancing" }
        state = TabTrainingState.Awaiting(createQuestion())
        return state
    }

    fun selectExercise(exercise: TabExercise): TabTrainingState {
        check(remainingGuideQuestions == 0) { "Finish the TAB guide before selecting an exercise" }
        val awaiting = state as? TabTrainingState.Awaiting
        check(awaiting != null && awaiting.selected.isEmpty()) {
            "Finish the current TAB question before changing exercises"
        }
        selectedExercise = exercise
        state = TabTrainingState.Awaiting(createQuestion())
        return state
    }

    private fun submitAwaiting(
        current: TabTrainingState.Awaiting,
        position: FretPosition,
    ): TabTrainingState {
        val expected = current.question.targets[current.selected.size]
        if (position != expected) {
            return TabTrainingState.CorrectionRequired(
                question = current.question,
                selected = current.selected,
                wrong = position,
                expected = expected,
            )
        }
        val updated = current.selected + position
        if (updated.size < current.question.targets.size) {
            return current.copy(selected = updated)
        }
        if (current.question.isGuide) {
            remainingGuideQuestions -= 1
            if (remainingGuideQuestions == 0 && !guideCompletionRecorded) {
                guideCompletionRecorded = true
                onGuideCompleted()
            }
        }
        return TabTrainingState.Completed(current.question, updated)
    }

    private fun createQuestion(): TabQuestion = if (remainingGuideQuestions > 0) {
        TabQuestion(
            id = nextQuestionId++,
            targets = listOf(randomPosition()),
            isGuide = true,
        )
    } else {
        TabQuestion(
            id = nextQuestionId++,
            targets = createFormalPhrase(),
            isGuide = false,
            exercise = selectedExercise,
        )
    }

    private fun createFormalPhrase(): List<FretPosition> {
        val length = when (selectedExercise) {
            TabExercise.SHORT_PHRASE -> if (random.nextBoolean()) 3 else 4
            TabExercise.ONE_MEASURE -> random.nextInt(from = 6, until = 9)
        }
        val phrase = mutableListOf(randomPosition())
        repeat(length - 1) {
            val previous = phrase.last()
            val candidates = firstPositionPositions.filter { candidate ->
                candidate != previous &&
                    if (formalQuestionCount < 4) {
                        candidate.string == previous.string &&
                            kotlin.math.abs(candidate.fret - previous.fret) <= 2
                    } else {
                        kotlin.math.abs(candidate.string - previous.string) <= 2 &&
                            kotlin.math.abs(candidate.fret - previous.fret) <= 3
                    }
            }
            phrase += candidates.random(random)
        }
        formalQuestionCount += 1
        return phrase
    }

    private fun randomPosition(): FretPosition = firstPositionPositions.random(random)

    private companion object {
        val firstPositionPositions = (1..6).flatMap { string ->
            (0..4).map { fret -> GuitarCore.getFretPosition(string, fret) }
        }
    }
}
