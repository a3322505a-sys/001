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
        val levelCounts = candidates.groupingBy { it.curriculumLevel }.eachCount()
        val question = weightedSample(candidates) { candidate ->
            val progress = progressById[candidate.knowledgeItemId]
            val itemWeight = when (candidate.weightPolicy) {
                QuestionWeightPolicy.MASTERY -> QuestionWeights.forProgress(progress)
                QuestionWeightPolicy.BOUNDED_PER_ITEM ->
                    PositionQuestionWeights.forProgress(progress)
            }
            itemWeight / levelCounts.getValue(candidate.curriculumLevel)
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
        return submitAnswerInternal(question, AnswerValue.Choice(choice.id), choice.label)
    }

    fun submitAnswer(answer: AnswerValue): AnswerResult {
        val question = currentQuestion ?: error("Generate a question before submitting an answer")
        if (submitted) return requireNotNull(lastResult).copy(accepted = false)
        val submittedLabel = when (answer) {
            is AnswerValue.Choice -> question.answerChoices.singleOrNull { it.id == answer.id }?.label
            is AnswerValue.FretPosition -> answer.string.toString() + "弦" + answer.fret + "品"
        } ?: return invalidResult(question, answer.toString())
        val modeMatches = when (question.answerMode) {
            AnswerMode.CHOICE -> answer is AnswerValue.Choice
            AnswerMode.FRETBOARD -> answer is AnswerValue.FretPosition
        }
        if (!modeMatches) return invalidResult(question, submittedLabel)
        return submitAnswerInternal(question, answer, submittedLabel)
    }

    fun submitChoice(choiceId: String): AnswerResult {
        val question = currentQuestion ?: error("Generate a question before submitting an answer")
        if (submitted) return requireNotNull(lastResult).copy(accepted = false)
        val choice = question.answerChoices.singleOrNull { it.id == choiceId }
            ?: return invalidResult(question, choiceId)
        return submitAnswerInternal(question, AnswerValue.Choice(choice.id), choice.label)
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

    private fun submitAnswerInternal(
        question: Question,
        answer: AnswerValue,
        submittedLabel: String,
    ): AnswerResult {
        val result = AnswerResult(
            accepted = true,
            isCorrect = answer == question.correctAnswerValue,
            submittedAnswer = submittedLabel,
            correctAnswer = question.correctAnswer,
            knowledgeItemId = question.knowledgeItemId,
            submittedChoiceId = (answer as? AnswerValue.Choice)?.id,
            correctChoiceId = (question.correctAnswerValue as? AnswerValue.Choice)?.id,
            submittedValue = answer,
            correctValue = question.correctAnswerValue,
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
        submittedValue = null,
        correctValue = question.correctAnswerValue,
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
