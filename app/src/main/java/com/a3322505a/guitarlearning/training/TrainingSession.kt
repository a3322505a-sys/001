package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
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
        val updated = progress.copy(
            attempts = attempts,
            correct = correct,
            streak = if (result.isCorrect) progress.streak + 1 else 0,
            lastSeenAt = seenAt,
            recentResults = (progress.recentResults + result.isCorrect).takeLast(20),
            seenDays = progress.seenDays + utcDay(seenAt),
        )
        return updated.copy(mastery = MasteryEvaluator.evaluate(updated))
    }

    private fun utcDay(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

/** Bridges the UI-free engine to local storage while keeping update rules out of Compose. */
class TrainingSession(
    private val engine: TrainingEngine,
    private val store: TrainingStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    sessionId: String = UUID.randomUUID().toString(),
) {
    private val factory = QuestionFactory()
    private var activeSession = Session(id = sessionId, startedAt = nowMs())

    init {
        store.saveSession(activeSession)
    }

    val currentSession: Session
        get() = activeSession

    fun currentSettings(): Settings = engine.settings()

    fun currentQuestion(): Question = engine.currentQuestion() ?: engine.generateQuestion()

    fun submitAnswer(answer: String): AnswerResult {
        val question = engine.currentQuestion()
            ?: error("Generate a question before submitting an answer")
        val result = engine.submitAnswer(answer)
        if (!result.accepted) return result

        val oldProgress = store.loadProgress(result.knowledgeItemId)
            ?: Progress(knowledgeItemId = result.knowledgeItemId)
        val newProgress = ProgressUpdater.record(oldProgress, result, nowMs())
        val item = store.findKnowledgeItem(result.knowledgeItemId)
            ?: factory.knowledgeItemFor(question)
        store.upsertKnowledgeItem(item.copy(status = newProgress.mastery))
        store.saveProgress(newProgress)

        activeSession = activeSession.copy(
            endedAt = null,
            questionCount = activeSession.questionCount + 1,
            correctCount = activeSession.correctCount + if (result.isCorrect) 1 else 0,
        )
        store.saveSession(activeSession)
        return result
    }

    fun nextQuestion(): Question = engine.nextQuestion()

    fun resetForSettings(settings: Settings): Question {
        engine.updateSettings(settings)
        store.saveSettings(settings)
        activeSession = Session(id = UUID.randomUUID().toString(), startedAt = nowMs())
        store.saveSession(activeSession)
        return engine.nextQuestion()
    }

    fun finish(): Session {
        activeSession = activeSession.copy(endedAt = nowMs())
        store.saveSession(activeSession)
        return activeSession
    }
}
