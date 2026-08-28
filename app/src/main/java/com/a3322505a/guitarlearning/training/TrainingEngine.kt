package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random

/**
 * Question generation and answer submission with no Compose or Android state.
 */
class TrainingEngine(
    settings: Settings = Settings(),
    private val random: Random = Random.Default,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val progressProvider: () -> List<Progress> = { emptyList() },
    private val enabledQuestionTypes: List<QuestionType> = QuestionType.entries,
) {
    private val factory = QuestionFactory()
    private val positions: List<FretPosition> = GuitarCore.allPositions(
        strings = settings.selectedStrings.sorted(),
        frets = settings.fretStart..settings.fretEnd,
        naturalOnly = true,
    ).also { require(it.isNotEmpty()) { "Question bank must contain a natural note" } }
    private val mappingNotes: List<String> = GuitarCore.fixedMappings.map { it.note }
    private val mappingDegrees: List<Int> = GuitarCore.fixedMappings.map { it.degree }
    private val questionTypes = enabledQuestionTypes.distinct().also {
        require(it.isNotEmpty()) { "At least one question type must be enabled" }
    }

    private var currentQuestion: Question? = null
    private var startedAtMs: Long? = null
    private var submitted = false
    private var lastResult: AnswerResult? = null

    fun generateQuestion(type: QuestionType? = null): Question {
        val candidates = if (type == null) {
            questionTypes.flatMap(::candidatesFor)
        } else {
            candidatesFor(type)
        }
        val progressById = progressProvider().associateBy { it.knowledgeItemId }
        val question = weightedSample(candidates) { question ->
            QuestionWeights.forProgress(progressById[question.knowledgeItemId])
        }
        currentQuestion = question
        startedAtMs = clockMs()
        submitted = false
        lastResult = null
        return question
    }

    fun submitAnswer(answer: String): AnswerResult {
        val question = currentQuestion ?: error("Generate a question before submitting an answer")
        if (submitted) {
            return requireNotNull(lastResult).copy(accepted = false)
        }
        if (answer !in question.choices) {
            return AnswerResult(
                accepted = false,
                isCorrect = false,
                submittedAnswer = answer,
                correctAnswer = question.correctAnswer,
                responseMs = elapsedMs(),
                knowledgeItemId = question.knowledgeItemId,
            )
        }
        val result = AnswerResult(
            accepted = true,
            isCorrect = answer == question.correctAnswer,
            submittedAnswer = answer,
            correctAnswer = question.correctAnswer,
            responseMs = elapsedMs(),
            knowledgeItemId = question.knowledgeItemId,
        )
        submitted = true
        lastResult = result
        return result
    }

    fun nextQuestion(): Question = generateQuestion()

    fun currentQuestion(): Question? = currentQuestion

    private fun candidatesFor(type: QuestionType): List<Question> = when (type) {
        QuestionType.FretToNote,
        QuestionType.FretToSolfege -> positions.map { factory.create(type, it) }
        QuestionType.NoteToSolfege,
        QuestionType.SolfegeToNote,
        QuestionType.NoteToDegree,
        QuestionType.SolfegeToDegree -> mappingNotes.map { factory.createForNote(type, it) }
        QuestionType.DegreeToNote,
        QuestionType.DegreeToSolfege -> mappingDegrees.map { factory.createForDegree(type, it) }
    }

    private fun <T> weightedSample(items: List<T>, weight: (T) -> Double): T {
        require(items.isNotEmpty()) { "Question bank must contain a question" }
        val total = items.sumOf(weight)
        var cursor = random.nextDouble() * total
        items.forEach { item ->
            cursor -= weight(item)
            if (cursor < 0.0) return item
        }
        return items.last()
    }

    private fun elapsedMs(): Long {
        val started = startedAtMs ?: error("Question timer has not started")
        return (clockMs() - started).coerceAtLeast(0L)
    }
}
