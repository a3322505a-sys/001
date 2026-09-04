package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.LevelProgress
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
    private val sessionMistakes = mutableListOf<SessionMistake>()

    init {
        store.saveSession(activeSession)
    }

    val currentSession: Session
        get() = activeSession

    fun currentStats(): SessionTrainingStats =
        SessionTrainingStats.from(activeSession, sessionMistakes)

    fun currentSettings(): Settings = engine.settings()

    fun levelProgress(level: Int): LevelProgress? = store.loadLevelProgress(level)

    fun currentQuestion(): Question = engine.currentQuestion() ?: engine.generateQuestion()

    fun submitAnswer(answer: String): AnswerResult {
        return recordAnswer(engine.submitAnswer(answer))
    }

    fun submitAnswer(answer: AnswerValue): AnswerResult {
        return recordAnswer(engine.submitAnswer(answer))
    }

    private fun recordAnswer(result: AnswerResult): AnswerResult {
        val question = engine.currentQuestion()
            ?: error("Generate a question before submitting an answer")
        if (!result.accepted) return result

        val oldProgress = store.loadProgress(result.knowledgeItemId)
            ?: Progress(knowledgeItemId = result.knowledgeItemId)
        val recordedProgress = ProgressUpdater.record(oldProgress, result, nowMs())
        val newProgress = if (question.weightPolicy == QuestionWeightPolicy.BOUNDED_PER_ITEM) {
            recordedProgress.copy(
                weight = PositionQuestionWeights.afterAnswer(
                    previousWeight = oldProgress.weight,
                    totalAttempts = recordedProgress.attempts,
                    isCorrect = result.isCorrect,
                ),
            )
        } else {
            recordedProgress
        }
        val item = store.findKnowledgeItem(result.knowledgeItemId)
            ?: factory.knowledgeItemFor(question)
        store.upsertKnowledgeItem(item.copy(status = newProgress.mastery))
        store.saveProgress(newProgress)
        recordCurriculumLevel(question, result)
        recordFirstPositionGrowth(question, result)
        recordChordShapeGrowth(question, result)
        recordSessionMistake(question, result)

        activeSession = activeSession.copy(
            endedAt = null,
            questionCount = activeSession.questionCount + 1,
            correctCount = activeSession.correctCount + if (result.isCorrect) 1 else 0,
        )
        store.saveSession(activeSession)
        return result
    }

    private fun recordSessionMistake(question: Question, result: AnswerResult) {
        if (result.isCorrect) return
        val position = when (val submitted = result.submittedValue) {
            is AnswerValue.FretPosition -> submitted
            is AnswerValue.FretSequence -> submitted.positions.lastOrNull()
            is AnswerValue.FretSet -> {
                val correctUniverse = question.correctUniversePositions.map {
                    AnswerValue.FretPosition(it.string, it.fret)
                }.toSet()
                submitted.positions.firstOrNull { it !in correctUniverse }
            }
            else -> question.fretPosition?.let { AnswerValue.FretPosition(it.string, it.fret) }
        } ?: return
        val targetNote = when (val correct = result.correctValue) {
            is AnswerValue.FretPosition ->
                GuitarCore.getFretPosition(correct.string, correct.fret).note
            is AnswerValue.FretSequence -> {
                val submitted = result.submittedValue as? AnswerValue.FretSequence
                val target = submitted?.positions?.lastIndex?.let(correct.positions::getOrNull)
                target?.let { GuitarCore.getFretPosition(it.string, it.fret).note }
            }
            else -> null
        } ?: question.note
        sessionMistakes += SessionMistake(
            targetNote = targetNote,
            string = position.string,
            fret = position.fret,
        )
    }

    private fun recordCurriculumLevel(question: Question, result: AnswerResult) {
        if (question.moduleId != TrainingModuleIds.FRET_NOTE) return
        val level = question.curriculumLevel
        val previous = store.loadLevelProgress(level) ?: LevelProgress(level)
        val updated = FretboardLevelRules.record(previous, result.isCorrect)
        store.saveLevelProgress(updated)

        val settings = engine.settings()
        if (
            level == settings.unlockedFretboardLevel &&
            level < FretboardLevelRules.MAXIMUM_LEVEL &&
            FretboardLevelRules.qualifies(updated)
        ) {
            val unlocked = settings.copy(unlockedFretboardLevel = level + 1)
            store.saveSettings(unlocked)
            engine.updateSettingsAfterAnswer(unlocked)
        }
    }

    private fun recordFirstPositionGrowth(question: Question, result: AnswerResult) {
        val settings = engine.settings()
        if (
            question.answerMode != AnswerMode.FRETBOARD_SET ||
            settings.noteTrainingRangeId != NoteTrainingRange.LOW_POSITION.name ||
            settings.firstPositionComplete
        ) {
            return
        }

        val updated = if (!settings.firstPositionBaselineComplete) {
            val baselineCovered = FirstFretboardModule()
                .buildQuestionBank(settings)
                .filter { it.curriculumLevel == 1 }
                .all { baselineQuestion ->
                    (store.loadProgress(baselineQuestion.knowledgeItemId)?.correct ?: 0) >= 1
                }
            if (!baselineCovered) return
            val first = requireNotNull(FirstPositionCurriculum.nextInactive(settings))
            settings.copy(
                firstPositionBaselineComplete = true,
                firstPositionActiveKnowledgeIds = setOf(FirstPositionCurriculum.id(first)),
            )
        } else {
            if (!result.isCorrect) return
            val payload = question.payload as? FretboardNoteSetPayload ?: return
            val focus = FirstPositionCurriculum.expansionPositions
                .lastOrNull {
                    FirstPositionCurriculum.id(it) in settings.firstPositionActiveKnowledgeIds
                }
                ?: return
            if (focus !in payload.targets) return
            val next = FirstPositionCurriculum.nextInactive(settings)
            if (next == null) {
                settings.copy(firstPositionComplete = true)
            } else {
                settings.copy(
                    firstPositionActiveKnowledgeIds =
                        settings.firstPositionActiveKnowledgeIds + FirstPositionCurriculum.id(next),
                )
            }
        }
        store.saveSettings(updated)
        engine.updateSettingsAfterAnswer(updated)
    }

    private fun recordChordShapeGrowth(question: Question, result: AnswerResult) {
        if (!result.isCorrect) return
        val payload = question.payload as? FretboardShapePayload ?: return
        val settings = engine.settings()
        if (
            payload.order != settings.unlockedChordShapeCount ||
            settings.unlockedChordShapeCount >= FirstPositionChordShapes.ordered.size
        ) {
            return
        }
        val updated = settings.copy(
            unlockedChordShapeCount = settings.unlockedChordShapeCount + 1,
        )
        store.saveSettings(updated)
        engine.updateSettingsAfterAnswer(updated)
    }

    fun consumeIntroduction(question: Question): String? {
        val introduction = CurriculumIntroductions.forQuestion(question) ?: return null
        val settings = engine.settings()
        if (introduction.id in settings.seenIntroductionIds) return null
        val updated = settings.copy(
            seenIntroductionIds = settings.seenIntroductionIds + introduction.id,
        )
        store.saveSettings(updated)
        engine.updateSettingsAfterAnswer(updated)
        return introduction.text
    }

    fun completeTabIntroduction() {
        val settings = engine.settings()
        if (settings.tabIntroductionCompleted) return
        val updated = settings.copy(tabIntroductionCompleted = true)
        store.saveSettings(updated)
        engine.updateSettingsAfterAnswer(updated)
    }

    fun nextQuestion(): Question = engine.nextQuestion()

    fun resetForSettings(settings: Settings): Question {
        engine.updateSettings(settings)
        store.saveSettings(settings)
        activeSession = Session(id = UUID.randomUUID().toString(), startedAt = nowMs())
        sessionMistakes.clear()
        store.saveSession(activeSession)
        return engine.nextQuestion()
    }

    fun finish(): Session {
        activeSession = activeSession.copy(endedAt = nowMs())
        store.saveSession(activeSession)
        return activeSession
    }
}
