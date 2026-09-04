package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.audio.PitchCatalog
import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.math.abs
import kotlin.random.Random

enum class StaffExercise(
    val label: String,
) {
    SINGLE("单音"),
    SHORT_PHRASE("2–4 音"),
    ONE_MEASURE("一小节"),
}

data class StaffTarget(
    val position: FretPosition,
    val soundingMidi: Int = PitchCatalog.forFretPosition(position).noteNumber,
) {
    init {
        require(position.fret in 0..4) { "Staff targets must stay inside first position" }
        require(GuitarCore.isNaturalNote(position.note)) { "Staff targets must use natural notes" }
        require(soundingMidi == PitchCatalog.forFretPosition(position).noteNumber) {
            "The displayed pitch and fret position must share one pitch source"
        }
    }
}

data class StaffQuestion(
    val id: Int,
    val exercise: StaffExercise,
    val targets: List<StaffTarget>,
) {
    init {
        when (exercise) {
            StaffExercise.SINGLE -> require(targets.size == 1)
            StaffExercise.SHORT_PHRASE -> require(targets.size in 2..4)
            StaffExercise.ONE_MEASURE -> require(targets.size in 6..8)
        }
    }
}

sealed interface StaffTrainingState {
    val question: StaffQuestion

    data class Awaiting(
        override val question: StaffQuestion,
        val selected: List<FretPosition> = emptyList(),
    ) : StaffTrainingState

    data class CorrectionRequired(
        override val question: StaffQuestion,
        val selected: List<FretPosition>,
        val wrong: FretPosition,
        val expected: FretPosition,
    ) : StaffTrainingState

    data class CorrectionConfirmed(
        override val question: StaffQuestion,
        val selected: List<FretPosition>,
        val wrong: FretPosition,
        val expected: FretPosition,
    ) : StaffTrainingState

    data class Completed(
        override val question: StaffQuestion,
        val selected: List<FretPosition>,
    ) : StaffTrainingState
}

class StaffTrainingStateMachine(
    initialExercise: StaffExercise = StaffExercise.SINGLE,
    private val random: Random = Random.Default,
) {
    private var nextQuestionId = 1

    var selectedExercise: StaffExercise = initialExercise
        private set

    var state: StaffTrainingState = StaffTrainingState.Awaiting(createQuestion())
        private set

    fun selectExercise(exercise: StaffExercise): StaffTrainingState {
        val awaiting = state as? StaffTrainingState.Awaiting
        check(awaiting != null && awaiting.selected.isEmpty()) {
            "Finish the current staff question before changing exercises"
        }
        selectedExercise = exercise
        state = StaffTrainingState.Awaiting(createQuestion())
        return state
    }

    fun submit(position: FretPosition): StaffTrainingState {
        state = when (val current = state) {
            is StaffTrainingState.Awaiting -> submitAwaiting(current, position)
            is StaffTrainingState.CorrectionRequired -> {
                if (position == current.expected) {
                    StaffTrainingState.CorrectionConfirmed(
                        current.question,
                        current.selected,
                        current.wrong,
                        current.expected,
                    )
                } else {
                    current
                }
            }
            is StaffTrainingState.CorrectionConfirmed,
            is StaffTrainingState.Completed -> current
        }
        return state
    }

    fun nextQuestion(): StaffTrainingState {
        check(
            state is StaffTrainingState.Completed ||
                state is StaffTrainingState.CorrectionConfirmed,
        ) { "The current staff question must be completed before advancing" }
        state = StaffTrainingState.Awaiting(createQuestion())
        return state
    }

    private fun submitAwaiting(
        current: StaffTrainingState.Awaiting,
        position: FretPosition,
    ): StaffTrainingState {
        val expected = current.question.targets[current.selected.size].position
        if (position != expected) {
            return StaffTrainingState.CorrectionRequired(
                current.question,
                current.selected,
                position,
                expected,
            )
        }
        val updated = current.selected + position
        return if (updated.size == current.question.targets.size) {
            StaffTrainingState.Completed(current.question, updated)
        } else {
            current.copy(selected = updated)
        }
    }

    private fun createQuestion(): StaffQuestion {
        val length = when (selectedExercise) {
            StaffExercise.SINGLE -> 1
            StaffExercise.SHORT_PHRASE -> random.nextInt(from = 2, until = 5)
            StaffExercise.ONE_MEASURE -> random.nextInt(from = 6, until = 9)
        }
        val positions = mutableListOf(firstPositionNaturals.random(random))
        repeat(length - 1) {
            val previous = positions.last()
            val previousMidi = PitchCatalog.forFretPosition(previous).noteNumber
            val candidates = firstPositionNaturals.filter { candidate ->
                candidate != previous &&
                    abs(PitchCatalog.forFretPosition(candidate).noteNumber - previousMidi) <= 5
            }
            positions += candidates.random(random)
        }
        return StaffQuestion(
            id = nextQuestionId++,
            exercise = selectedExercise,
            targets = positions.map(::StaffTarget),
        )
    }

    private companion object {
        val firstPositionNaturals = GuitarCore.allPositions(frets = 0..4, naturalOnly = true)
    }
}

/** Guitar sounds one octave below its treble-clef notation; bottom staff line E4 is step zero. */
fun writtenStaffStepForSoundingMidi(soundingMidi: Int): Int {
    val writtenMidi = soundingMidi + 12
    val pitchClass = writtenMidi.mod(12)
    val letterIndex = NATURAL_LETTER_INDEX[pitchClass]
        ?: error("Staff training only supports natural-note pitches")
    val octave = writtenMidi / 12 - 1
    val diatonicIndex = octave * 7 + letterIndex
    val bottomLineE4 = 4 * 7 + 2
    return diatonicIndex - bottomLineE4
}

private val NATURAL_LETTER_INDEX = mapOf(
    0 to 0,
    2 to 1,
    4 to 2,
    5 to 3,
    7 to 4,
    9 to 5,
    11 to 6,
)
