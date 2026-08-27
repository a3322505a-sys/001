package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random

/**
 * V0.1 question generation and answer submission. It deliberately has no Compose or Android
 * state; persistence and mastery weighting are added in their later checkpoints.
 */
class TrainingEngine(
    settings: Settings = Settings(),
    private val random: Random = Random.Default,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val factory = QuestionFactory()
    private val positions: List<FretPosition> = GuitarCore.allPositions(
        strings = settings.selectedStrings.sorted(),
        frets = settings.fretStart..settings.fretEnd,
        // P05 is intentionally natural-only even if a future settings screen supplies false.
        naturalOnly = true,
    ).also { require(it.isNotEmpty()) { "Question bank must contain a natural note" } }
    private val notes: List<String> = positions.map { it.note }.distinct()
    private val questionTypes = QuestionType.entries.toList()

    private var currentQuestion: Question? = null
    private var startedAtMs: Long? = null
    private var submitted = false
    private var lastResult: AnswerResult? = null

    fun generateQuestion(type: QuestionType? = null): Question {
        val selectedType = type ?: questionTypes.random(random)
        val question = when (selectedType) {
            QuestionType.FretToNote,
            QuestionType.FretToSolfege -> factory.create(selectedType, positions.random(random))
            QuestionType.NoteToSolfege,
            QuestionType.SolfegeToNote -> factory.createForNote(selectedType, notes.random(random))
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

    private fun elapsedMs(): Long {
        val started = startedAtMs ?: error("Question timer has not started")
        return (clockMs() - started).coerceAtLeast(0L)
    }
}
