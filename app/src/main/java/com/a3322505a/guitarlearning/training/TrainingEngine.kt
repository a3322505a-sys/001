package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random

/** Module-neutral question selection and answer submission with no Compose or Android state. */
class TrainingEngine(
    settings: Settings = Settings(),
    private val random: Random = Random.Default,
    private val progressProvider: () -> List<Progress> = { emptyList() },
    enabledQuestionTypes: List<QuestionType> = QuestionType.entries,
    module: TrainingModule? = null,
) {
    private val trainingModule = module ?: LegacyTrainingModule(enabledQuestionTypes)
    private var currentSettings = settings
    private var questionBank = trainingModule.buildQuestionBank(settings)
    private var currentQuestion: Question? = null
    private var submitted = false
    private var lastResult: AnswerResult? = null

    fun generateQuestion(type: QuestionType? = null): Question {
        val candidates = if (type == null) questionBank else questionBank.filter { it.type == type }
        require(candidates.isNotEmpty()) { "Question bank does not contain the requested type" }
        val progressById = progressProvider().associateBy { it.knowledgeItemId }
        val question = weightedSample(candidates) { candidate ->
            val progress = progressById[candidate.knowledgeItemId]
            when (candidate.weightPolicy) {
                QuestionWeightPolicy.MASTERY -> QuestionWeights.forProgress(progress)
                QuestionWeightPolicy.BOUNDED_PER_ITEM ->
                    PositionQuestionWeights.forProgress(progress)
            }
        }
        currentQuestion = question
        submitted = false
        lastResult = null
        return question
    }

    fun submitAnswer(answer: String): AnswerResult {
        val question = currentQuestion ?: error("Generate a question before submitting an answer")
        if (submitted) return requireNotNull(lastResult).copy(accepted = false)
        val choice = question.choiceForLabel(answer) ?: return invalidResult(question, answer)
        return submitChoiceInternal(question, choice)
    }

    fun submitChoice(choiceId: String): AnswerResult {
        val question = currentQuestion ?: error("Generate a question before submitting an answer")
        if (submitted) return requireNotNull(lastResult).copy(accepted = false)
        val choice = question.answerChoices.singleOrNull { it.id == choiceId }
            ?: return invalidResult(question, choiceId)
        return submitChoiceInternal(question, choice)
    }

    fun nextQuestion(): Question = generateQuestion()

    fun currentQuestion(): Question? = currentQuestion

    fun settings(): Settings = currentSettings

    fun moduleId(): String = trainingModule.id

    fun updateSettings(newSettings: Settings) {
        currentSettings = newSettings
        questionBank = trainingModule.buildQuestionBank(newSettings)
        currentQuestion = null
        submitted = false
        lastResult = null
    }

    private fun submitChoiceInternal(question: Question, choice: AnswerChoice): AnswerResult {
        val result = AnswerResult(
            accepted = true,
            isCorrect = choice.id == question.correctChoiceId,
            submittedAnswer = choice.label,
            correctAnswer = question.correctAnswer,
            knowledgeItemId = question.knowledgeItemId,
            submittedChoiceId = choice.id,
            correctChoiceId = question.correctChoiceId,
        )
        submitted = true
        lastResult = result
        return result
    }

    private fun invalidResult(question: Question, submitted: String): AnswerResult = AnswerResult(
        accepted = false,
        isCorrect = false,
        submittedAnswer = submitted,
        correctAnswer = question.correctAnswer,
        knowledgeItemId = question.knowledgeItemId,
        submittedChoiceId = null,
        correctChoiceId = question.correctChoiceId,
    )

    private fun <T> weightedSample(items: List<T>, weight: (T) -> Double): T {
        val total = items.sumOf(weight)
        require(total > 0.0) { "Question bank must have positive total weight" }
        var cursor = random.nextDouble() * total
        items.forEach { item ->
            cursor -= weight(item)
            if (cursor < 0.0) return item
        }
        return items.last()
    }
}
