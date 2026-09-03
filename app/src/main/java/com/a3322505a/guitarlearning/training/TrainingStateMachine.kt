package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

const val CORRECT_FEEDBACK_DURATION_MS = 400L

/** Explicit lifecycle of one question from answering to manual or automatic advancement. */
sealed interface QuestionState {
    val question: Question

    data class AwaitingAnswer(
        override val question: Question,
    ) : QuestionState

    data class AwaitingSequenceAnswer(
        override val question: Question,
    ) : QuestionState

    data class AwaitingSetAnswer(
        override val question: Question,
    ) : QuestionState

    data class SetProgress(
        override val question: Question,
        val selectedPositions: Set<AnswerValue.FretPosition>,
        val extraCorrectPositions: Set<AnswerValue.FretPosition> = emptySet(),
    ) : QuestionState

    data class SetCompleted(
        override val question: Question,
        val result: AnswerResult,
        val selectedPositions: Set<AnswerValue.FretPosition>,
        val extraCorrectPositions: Set<AnswerValue.FretPosition> = emptySet(),
    ) : QuestionState

    data class SetCorrectionRequired(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val confirmedPositions: Set<AnswerValue.FretPosition>,
    ) : QuestionState

    data class SetCorrectionProgress(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val confirmedPositions: Set<AnswerValue.FretPosition>,
    ) : QuestionState

    data class SetCorrectionConfirmed(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val confirmedPositions: Set<AnswerValue.FretPosition>,
    ) : QuestionState

    data class SequenceProgress(
        override val question: Question,
        val selectedPositions: List<AnswerValue.FretPosition>,
    ) : QuestionState

    data class SequenceCompleted(
        override val question: Question,
        val result: AnswerResult,
        val selectedPositions: List<AnswerValue.FretPosition>,
    ) : QuestionState

    data class Correct(
        override val question: Question,
        val result: AnswerResult,
    ) : QuestionState

    data class Incorrect(
        override val question: Question,
        val result: AnswerResult,
    ) : QuestionState

    data class CorrectionRequired(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val correctPosition: AnswerValue.FretPosition,
        val confirmedPositions: List<AnswerValue.FretPosition> = emptyList(),
    ) : QuestionState

    data class CorrectionConfirmed(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val correctPosition: AnswerValue.FretPosition,
        val confirmedPositions: List<AnswerValue.FretPosition> = emptyList(),
    ) : QuestionState
}

/** Coordinates accepted answers, stable review feedback, and next-question actions. */
class TrainingStateMachine(
    private val session: TrainingSession,
) {
    private var current: QuestionState = awaitingState(session.nextQuestion())

    val state: QuestionState
        get() = current

    fun submitAnswer(answer: String): QuestionState {
        return submitAwaitingAnswer { session.submitAnswer(answer) }
    }

    fun submitAnswer(answer: AnswerValue): QuestionState {
        if (answer is AnswerValue.FretPosition && isSetCorrectionInProgress()) {
            return submitSetCorrectionPosition(answer)
        }
        val correction = current as? QuestionState.CorrectionRequired
        if (correction != null) {
            if (answer == correction.correctPosition) {
                current = QuestionState.CorrectionConfirmed(
                    question = correction.question,
                    result = correction.result,
                    wrongPosition = correction.wrongPosition,
                    correctPosition = correction.correctPosition,
                    confirmedPositions = correction.confirmedPositions,
                )
            }
            return current
        }
        if (answer is AnswerValue.FretPosition && isSequenceInProgress()) {
            return submitSequencePosition(answer)
        }
        if (answer is AnswerValue.FretPosition && isSetInProgress()) {
            return submitSetPosition(answer)
        }
        if (answer is AnswerValue.FretSet && current is QuestionState.AwaitingSetAnswer) {
            val question = current.question
            val expected = (question.correctAnswerValue as AnswerValue.FretSet).positions
            val correctUniverse = question.correctUniversePositions.map {
                AnswerValue.FretPosition(it.string, it.fret)
            }.toSet()
            val wrongPosition = answer.positions.firstOrNull { it !in correctUniverse }
            val confirmed = answer.positions.intersect(expected)
            val extraCorrect = answer.positions.intersect(correctUniverse) - expected
            if (wrongPosition == null && confirmed != expected) {
                current = QuestionState.SetProgress(question, confirmed, extraCorrect)
                return current
            }
            val submitted = if (wrongPosition == null) AnswerValue.FretSet(expected) else answer
            val result = session.submitAnswer(submitted)
            current = if (wrongPosition == null && result.accepted && result.isCorrect) {
                QuestionState.SetCompleted(question, result, expected, extraCorrect)
            } else if (result.accepted) {
                QuestionState.SetCorrectionRequired(
                    question = question,
                    result = result,
                    wrongPosition = requireNotNull(wrongPosition),
                    confirmedPositions = confirmed,
                )
            } else {
                current
            }
            return current
        }
        if (answer is AnswerValue.FretSequence && current is QuestionState.AwaitingSequenceAnswer) {
            val question = current.question
            val result = session.submitAnswer(answer)
            current = if (result.accepted && result.isCorrect) {
                QuestionState.SequenceCompleted(question, result, answer.positions)
            } else if (result.accepted) {
                QuestionState.Incorrect(question, result)
            } else {
                current
            }
            return current
        }
        return submitAwaitingAnswer { session.submitAnswer(answer) }
    }

    private fun isSequenceInProgress(): Boolean =
        current is QuestionState.AwaitingSequenceAnswer ||
            current is QuestionState.SequenceProgress

    private fun isSetInProgress(): Boolean =
        current is QuestionState.AwaitingSetAnswer || current is QuestionState.SetProgress

    private fun isSetCorrectionInProgress(): Boolean =
        current is QuestionState.SetCorrectionRequired ||
            current is QuestionState.SetCorrectionProgress

    private fun submitSetPosition(answer: AnswerValue.FretPosition): QuestionState {
        val question = current.question
        val selected = (current as? QuestionState.SetProgress)?.selectedPositions.orEmpty()
        val extraCorrect = (current as? QuestionState.SetProgress)?.extraCorrectPositions.orEmpty()
        val expected = (question.correctAnswerValue as AnswerValue.FretSet).positions
        val correctUniverse = question.correctUniversePositions.map {
            AnswerValue.FretPosition(it.string, it.fret)
        }.toSet()
        if (answer in selected || answer in extraCorrect) return current

        if (answer !in correctUniverse) {
            val result = session.submitAnswer(AnswerValue.FretSet(selected + answer))
            if (result.accepted) {
                current = QuestionState.SetCorrectionRequired(
                    question = question,
                    result = result,
                    wrongPosition = answer,
                    confirmedPositions = selected,
                )
            }
            return current
        }

        if (answer !in expected) {
            current = QuestionState.SetProgress(
                question = question,
                selectedPositions = selected,
                extraCorrectPositions = extraCorrect + answer,
            )
            return current
        }

        val updated = selected + answer
        current = if (updated == expected) {
            val result = session.submitAnswer(AnswerValue.FretSet(updated))
            check(result.accepted && result.isCorrect) {
                "A completed fret set must be accepted as correct"
            }
            QuestionState.SetCompleted(question, result, updated, extraCorrect)
        } else {
            QuestionState.SetProgress(question, updated, extraCorrect)
        }
        return current
    }

    private fun submitSetCorrectionPosition(answer: AnswerValue.FretPosition): QuestionState {
        val correctionRequired = current as? QuestionState.SetCorrectionRequired
        val correctionProgress = current as? QuestionState.SetCorrectionProgress
        val question = current.question
        val result = correctionRequired?.result ?: requireNotNull(correctionProgress).result
        val wrongPosition = correctionRequired?.wrongPosition
            ?: requireNotNull(correctionProgress).wrongPosition
        val confirmed = correctionRequired?.confirmedPositions
            ?: requireNotNull(correctionProgress).confirmedPositions
        val expected = (question.correctAnswerValue as AnswerValue.FretSet).positions
        if (answer !in expected || answer in confirmed) return current

        val updated = confirmed + answer
        current = if (updated == expected) {
            QuestionState.SetCorrectionConfirmed(
                question = question,
                result = result,
                wrongPosition = wrongPosition,
                confirmedPositions = updated,
            )
        } else {
            QuestionState.SetCorrectionProgress(
                question = question,
                result = result,
                wrongPosition = wrongPosition,
                confirmedPositions = updated,
            )
        }
        return current
    }

    private fun submitSequencePosition(answer: AnswerValue.FretPosition): QuestionState {
        val question = current.question
        val selected = (current as? QuestionState.SequenceProgress)?.selectedPositions.orEmpty()
        val expected = (question.correctAnswerValue as AnswerValue.FretSequence).positions
        val expectedPosition = expected[selected.size]

        if (answer != expectedPosition) {
            val result = session.submitAnswer(AnswerValue.FretSequence(selected + answer))
            if (result.accepted) {
                current = QuestionState.CorrectionRequired(
                    question = question,
                    result = result,
                    wrongPosition = answer,
                    correctPosition = expectedPosition,
                    confirmedPositions = selected,
                )
            }
            return current
        }

        val updated = selected + answer
        current = if (updated.size == expected.size) {
            val result = session.submitAnswer(AnswerValue.FretSequence(updated))
            check(result.accepted && result.isCorrect) {
                "A completed sequence must be accepted as correct"
            }
            QuestionState.SequenceCompleted(question, result, updated)
        } else {
            QuestionState.SequenceProgress(question, updated)
        }
        return current
    }

    private fun submitAwaitingAnswer(submit: () -> AnswerResult): QuestionState {
        val awaiting = current as? QuestionState.AwaitingAnswer ?: return current
        val result = submit()
        if (!result.accepted) return current
        val submittedPosition = result.submittedValue as? AnswerValue.FretPosition
        val correctPosition = result.correctValue as? AnswerValue.FretPosition

        current = if (result.isCorrect) {
            QuestionState.Correct(awaiting.question, result)
        } else if (submittedPosition != null && correctPosition != null) {
            QuestionState.CorrectionRequired(
                question = awaiting.question,
                result = result,
                wrongPosition = submittedPosition,
                correctPosition = correctPosition,
            )
        } else {
            QuestionState.Incorrect(awaiting.question, result)
        }
        return current
    }

    fun nextQuestion(): QuestionState {
        check(
            current is QuestionState.Correct ||
                current is QuestionState.Incorrect ||
                current is QuestionState.CorrectionConfirmed ||
                current is QuestionState.SequenceCompleted ||
                current is QuestionState.SetCompleted ||
                current is QuestionState.SetCorrectionConfirmed,
        ) {
            "The current question must be completed before advancing"
        }
        current = awaitingState(session.nextQuestion())
        return current
    }

    /** Starts a new round while retaining per-item learning progress. */
    fun resetRound(settings: Settings): QuestionState {
        current = awaitingState(session.resetForSettings(settings))
        return current
    }

    fun resetNoteTrainingRange(range: NoteTrainingRange): QuestionState =
        resetRound(range.applyTo(session.currentSettings()))

    private fun awaitingState(question: Question): QuestionState =
        when (question.answerMode) {
            AnswerMode.FRETBOARD_SEQUENCE -> QuestionState.AwaitingSequenceAnswer(question)
            AnswerMode.FRETBOARD_SET -> QuestionState.AwaitingSetAnswer(question)
            else -> QuestionState.AwaitingAnswer(question)
        }
}
