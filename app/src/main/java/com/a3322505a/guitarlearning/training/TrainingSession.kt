package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Session
import com.a3322505a.guitarlearning.storage.TrainingStore
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Pure progress arithmetic used by the session coordinator and unit tests. */
object ProgressUpdater {
    fun record(progress: Progress, result: AnswerResult, seenAt: Long): Progress {
        require(result.accepted) { "Only accepted answers update progress" }
        val attempts = progress.attempts + 1
        val correct = progress.correct + if (result.isCorrect) 1 else 0
        val avgResponseMs = (
            progress.avgResponseMs * progress.attempts + result.responseMs
            ) / attempts
        return progress.copy(
            attempts = attempts,
            correct = correct,
            streak = if (result.isCorrect) progress.streak + 1 else 0,
            avgResponseMs = avgResponseMs,
            lastResponseMs = result.responseMs,
            lastSeenAt = seenAt,
            recentResults = (progress.recentResults + result.isCorrect).takeLast(20),
            seenDays = progress.seenDays + utcDay(seenAt),
        )
    }

    private fun utcDay(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

/** Bridges the UI-free engine to local storage while keeping update rules out of Compose. */
class TrainingSession(
    private val engine: TrainingEngine,
    private val store: TrainingStore,
    private val clockMs: () -> Long = System::currentTimeMillis,
    sessionId: String = UUID.randomUUID().toString(),
) {
    private val factory = QuestionFactory()
    private var activeSession = Session(id = sessionId, startedAt = clockMs())

    init {
        store.saveSession(activeSession)
    }

    val currentSession: Session
        get() = activeSession

    fun currentQuestion(): Question = engine.currentQuestion() ?: engine.generateQuestion()

    fun submitAnswer(answer: String): AnswerResult {
        val question = engine.currentQuestion()
            ?: error("Generate a question before submitting an answer")
        val result = engine.submitAnswer(answer)
        if (!result.accepted) return result

        val oldProgress = store.loadProgress(result.knowledgeItemId)
            ?: Progress(knowledgeItemId = result.knowledgeItemId)
        val newProgress = ProgressUpdater.record(oldProgress, result, clockMs())
        val item = store.findKnowledgeItem(result.knowledgeItemId)
            ?: factory.knowledgeItemFor(question)
        store.upsertKnowledgeItem(item.copy(status = newProgress.mastery))
        store.saveProgress(newProgress)

        activeSession = activeSession.copy(
            endedAt = null,
            questionCount = activeSession.questionCount + 1,
            correctCount = activeSession.correctCount + if (result.isCorrect) 1 else 0,
            avgResponseMs = (
                activeSession.avgResponseMs * activeSession.questionCount + result.responseMs
                ) / (activeSession.questionCount + 1),
        )
        store.saveSession(activeSession)
        return result
    }

    fun nextQuestion(): Question = engine.nextQuestion()

    fun finish(): Session {
        activeSession = activeSession.copy(endedAt = clockMs())
        store.saveSession(activeSession)
        return activeSession
    }
}
