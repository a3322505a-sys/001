package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.core.FretPosition

/** Plays the tapped physical pitch and independently forwards the same tap for judgement. */
fun handleFretboardTap(
    position: FretPosition,
    pitchPlayer: PitchPlayer,
    onAnswer: (FretPosition) -> Unit,
) {
    pitchPlayer.play(PitchCue(listOf(PitchCatalog.forFretPosition(position))))
    onAnswer(position)
}
