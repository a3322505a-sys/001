package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Choice-only questions keep their prompt and answers together, including with no feedback. */
@Composable
internal fun SymbolTaskLayout(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            TrainingPrompt(state)
            TaskNotation(state)
            state.relation?.let { RelationContent(it) { onEvent(TrainingEvent.Demonstrate) } }
            if (state.options.isNotEmpty()) AnswerOptions(state.options, { onEvent(TrainingEvent.Answer(it)) })
            TrainingMessage(state)
        }
    }
}

@Composable
private fun TaskNotation(state: TrainingUiState) {
    state.tab?.let { TabPrompt(it, Modifier.width(160.dp)) }
    state.notation?.let { CompactNotation(it, state.notationIndex) }
}

/** The header is measured by the parent; this sees only the actual remaining height. */
@Composable
internal fun BoardTaskLayout(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    val board = requireNotNull(state.board)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val workspaceHeight = maxHeight
        val compact = workspaceHeight < 220.dp * scale
        val prompt: @Composable ColumnScope.() -> Unit = {
            TrainingPrompt(state)
            TaskNotation(state)
            state.relation?.let { RelationContent(it) { onEvent(TrainingEvent.Demonstrate) } }
        }
        val answers: @Composable () -> Unit = {
            if (state.options.isNotEmpty()) AnswerOptions(state.options,
                { onEvent(TrainingEvent.Answer(it)) }, Modifier.fillMaxWidth())
        }
        if (compact) {
            // Exceptional short/large-text windows scroll as one page, with no clipped controls.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt()
                TeachingFretboard(board, { onEvent(TrainingEvent.Position(it)) },
                    Modifier.fillMaxWidth().height(192.dp))
                answers()
                Box(Modifier.fillMaxWidth().height(64.dp * scale).verticalScroll(rememberScrollState())) {
                    TrainingMessage(state)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max = workspaceHeight * .28f)
                    .verticalScroll(rememberScrollState()), content = prompt)
                TeachingFretboard(board, { onEvent(TrainingEvent.Position(it)) },
                    Modifier.fillMaxWidth().weight(1f))
                Box(Modifier.fillMaxWidth().heightIn(max = workspaceHeight * .3f)
                    .verticalScroll(rememberScrollState())) { answers() }
                // A stable footer prevents correction/audio state from moving the board or answers.
                Box(Modifier.fillMaxWidth().height(48.dp * scale).verticalScroll(rememberScrollState())) {
                    TrainingMessage(state)
                }
            }
        }
    }
}

@Composable
internal fun TrainingBoardWorkspace(board: FretboardUiState, onPosition: (PositionTapped) -> Unit,
                                    information: @Composable ColumnScope.() -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight < 240.dp * scale) {
            // A short window or large type must scroll rather than crush six strings or clip controls.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                information()
                TeachingFretboard(board, onPosition, Modifier.fillMaxWidth().height(240.dp * scale))
            }
        } else {
            val boardHeight = (maxHeight * .6f).coerceIn(180.dp * scale, 280.dp * scale)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = information)
                TeachingFretboard(board, onPosition, Modifier.fillMaxWidth().height(boardHeight))
            }
        }
    }
}

/** A chord is the main content; rotation also changes how much width its pane receives. */
@Composable
internal fun ChordTaskLayout(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit, legend: Boolean, closeLegend: () -> Unit) {
    val board = requireNotNull(state.board)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val controls: @Composable ColumnScope.() -> Unit = {
        TrainingPrompt(state)
        state.chordControls?.let { ChordInputControls(it, onEvent) }
        if (state.options.isNotEmpty()) AnswerOptions(state.options, { onEvent(TrainingEvent.Answer(it)) })
        if (legend) FingerLegend(closeLegend)
        TrainingMessage(state)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 500.dp * scale && maxHeight >= 220.dp * scale) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TeachingFretboard(board, { onEvent(TrainingEvent.Position(it)) },
                    Modifier.weight(if (board.chordVertical) .5f else .6f).fillMaxHeight())
                Column(Modifier.weight(if (board.chordVertical) .5f else .4f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = controls)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeachingFretboard(board, { onEvent(TrainingEvent.Position(it)) }, Modifier.fillMaxWidth().height(300.dp * scale))
                controls()
            }
        }
    }
}
