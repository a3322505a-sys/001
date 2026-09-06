package com.a3322505a.guitarlearning.learning

/** Public presentation contracts. No task, evaluator, repository or player is exposed. */
enum class BoardInteraction { DISABLED, AUDITION, ANSWER }
enum class MarkRole { TARGET, REFERENCE, CORRECT, WRONG }
data class BoardMark(val coordinate: Coordinate, val role: MarkRole, val label: String = "", val band: Boolean = false)
data class FingerUiSpan(val finger: Int, val fret: Int, val firstString: Int, val lastString: Int)
data class ChordToneUi(val coordinate: Coordinate, val label: String = "", val rootRing: Boolean = false, val dot: Boolean = false)
data class ChordOverlayUiState(
    val fingers: List<FingerUiSpan> = emptyList(),
    val tones: List<ChordToneUi> = emptyList(),
    val openMutedLabels: Map<Int, String> = emptyMap(),
)
data class FretboardUiState(
    val viewId: String,
    val firstFret: Int = 0,
    val lastFret: Int = 4,
    val interactivePositions: Set<Coordinate> = emptySet(),
    val interaction: BoardInteraction = BoardInteraction.DISABLED,
    val marks: List<BoardMark> = emptyList(),
    val chord: ChordOverlayUiState? = null,
    val stringLabel: String? = null,
    val fretLabel: Pair<Int, String>? = null,
    val answerPositions: Set<Coordinate> = interactivePositions,
)
data class PositionTapped(val viewId: String, val coordinate: Coordinate)

data class AudioUiState(val playing: Boolean = false, val message: String? = null, val failed: Boolean = false)
data class AnswerOptionUi(val value: String, val role: MarkRole? = null, val enabled: Boolean = true)
data class RelationUiState(val lines: List<String>, val demonstrationLabel: String?, val demonstrationEnabled: Boolean)
data class ChordControlsUiState(val string: Int, val progress: String, val enabled: Boolean, val canDemonstrate: Boolean)
data class TrainingUiState(
    val taskId: String? = null,
    val title: String = "",
    val summary: String = "进度已保存。",
    val busy: Boolean = false,
    val board: FretboardUiState? = null,
    val tab: Coordinate? = null,
    val notation: NotationPrompt? = null,
    val notationIndex: Int = 0,
    val message: String? = null,
    val wrong: Boolean = false,
    val options: List<AnswerOptionUi> = emptyList(),
    val relation: RelationUiState? = null,
    val chordControls: ChordControlsUiState? = null,
    val showLegend: Boolean = false,
    val hasChord: Boolean = false,
    val canHint: Boolean = false,
    val hintLabel: String = "提示",
    val canNext: Boolean = false,
    val autoNextDelayMs: Long? = null,
    val soundEnabled: Boolean = true,
    val canReplay: Boolean = false,
    val audio: AudioUiState = AudioUiState(),
    val pilot: PilotControlsUi? = null,
)
sealed interface TrainingEvent {
    data class Position(val tap: PositionTapped) : TrainingEvent
    data class Answer(val symbol: String) : TrainingEvent
    data class Fingering(val id: String) : TrainingEvent
    data object PilotPlay : TrainingEvent
    data class PilotFinish(val rating: String?, val comment: String) : TrainingEvent
    data class PilotTempo(val bpm: Int) : TrainingEvent
    data object PilotLoop : TrainingEvent
    data object PilotCompare : TrainingEvent
    data object PilotMetronome : TrainingEvent
    data object RetryAudio : TrainingEvent
    data object Replay : TrainingEvent
    data object Demonstrate : TrainingEvent
    data object Hint : TrainingEvent
    data object Next : TrainingEvent
    data object OpenString : TrainingEvent
    data object MuteString : TrainingEvent
    data object EnableSound : TrainingEvent
    data object LegendSeen : TrainingEvent
    data object Back : TrainingEvent
    data object End : TrainingEvent
}
