package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.random.Random

enum class CombinedStep {
    FIRST,
    SECOND,
}

data class CombinedQuestion(
    val id: Int,
    val note: String,
    val solfege: String,
    val degree: Int,
    val sourceType: AnswerKind,
    val firstTargetType: AnswerKind,
    val secondTargetType: AnswerKind,
) {
    fun valueFor(kind: AnswerKind): String = when (kind) {
        AnswerKind.NOTE -> note
        AnswerKind.SOLFEGE -> solfege
        AnswerKind.DEGREE -> degree.toString()
    }

    fun targetFor(step: CombinedStep): AnswerKind = when (step) {
        CombinedStep.FIRST -> firstTargetType
        CombinedStep.SECOND -> secondTargetType
    }

    fun promptFor(step: CombinedStep): String = when (step) {
        CombinedStep.FIRST -> valueFor(sourceType) + " = ?"
        CombinedStep.SECOND ->
            valueFor(sourceType) + " = " + valueFor(firstTargetType) + " = ?"
    }
}

class CombinedQuestionFactory(
    private val random: Random = Random.Default,
) {
    private var nextId = 0

    fun create(): CombinedQuestion {
        val mapping = GuitarCore.fixedMappings[random.nextInt(GuitarCore.fixedMappings.size)]
        val path = AnswerKind.entries.shuffled(random)
        return CombinedQuestion(
            id = nextId++,
            note = mapping.note,
            solfege = mapping.solfege,
            degree = mapping.degree,
            sourceType = path[0],
            firstTargetType = path[1],
            secondTargetType = path[2],
        )
    }

    fun shuffledAnswers(kind: AnswerKind): List<String> =
        AnswerOptions.forKind(kind).shuffled(random)
}

sealed interface CombinedMappingState {
    val question: CombinedQuestion
    val step: CombinedStep
    val choices: List<String>
    val correctAnswer: String

    val prompt: String
        get() = question.promptFor(step)

    data class AwaitingAnswer(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
    ) : CombinedMappingState

    data class Correct(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
        val submittedAnswer: String,
        val completesQuestion: Boolean,
    ) : CombinedMappingState

    data class Incorrect(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
        val submittedAnswer: String,
    ) : CombinedMappingState
}

/**
 * Coordinates one complete three-way mapping question. Statistics are updated once per
 * complete question, never once per tap.
 */
class CombinedMappingStateMachine(
    random: Random = Random.Default,
) {
    private val factory = CombinedQuestionFactory(random)
    private var current: CombinedMappingState = awaiting(factory.create(), CombinedStep.FIRST)

    var correctCount: Int = 0
        private set

    var errorCount: Int = 0
        private set

    val state: CombinedMappingState
        get() = current

    fun submitAnswer(answer: String): CombinedMappingState {
        val awaiting = current as? CombinedMappingState.AwaitingAnswer ?: return current
        if (answer !in awaiting.choices) return current

        current = if (answer == awaiting.correctAnswer) {
            val completesQuestion = awaiting.step == CombinedStep.SECOND
            if (completesQuestion) correctCount += 1
            CombinedMappingState.Correct(
                question = awaiting.question,
                step = awaiting.step,
                choices = awaiting.choices,
                correctAnswer = awaiting.correctAnswer,
                submittedAnswer = answer,
                completesQuestion = completesQuestion,
            )
        } else {
            errorCount += 1
            CombinedMappingState.Incorrect(
                question = awaiting.question,
                step = awaiting.step,
                choices = awaiting.choices,
                correctAnswer = awaiting.correctAnswer,
                submittedAnswer = answer,
            )
        }
        return current
    }

    fun advanceAfterCorrect(): CombinedMappingState {
        val correct = current as? CombinedMappingState.Correct ?: return current
        current = if (correct.completesQuestion) {
            awaiting(factory.create(), CombinedStep.FIRST)
        } else {
            awaiting(correct.question, CombinedStep.SECOND)
        }
        return current
    }

    fun nextQuestion(): CombinedMappingState {
        check(current is CombinedMappingState.Incorrect) {
            "Only an incorrect question requires manual advancement"
        }
        current = awaiting(factory.create(), CombinedStep.FIRST)
        return current
    }

    private fun awaiting(
        question: CombinedQuestion,
        step: CombinedStep,
    ): CombinedMappingState.AwaitingAnswer {
        val target = question.targetFor(step)
        return CombinedMappingState.AwaitingAnswer(
            question = question,
            step = step,
            choices = factory.shuffledAnswers(target),
            correctAnswer = question.valueFor(target),
        )
    }
}
