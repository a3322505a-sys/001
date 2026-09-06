package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme

/** Fixtures deliberately construct no curriculum, ViewModel, database or audio output. */
private val previewBoard = FretboardUiState("preview:b3", marks = listOf(BoardMark(Coordinate(3, 4), MarkRole.TARGET, "B")))

@Preview(widthDp = 840, heightDp = 390, showBackground = true)
@Composable
private fun TrainingContractPreview() {
    GuitarLearningTheme("clear") {
        TrainingScreen(TrainingUiState(taskId = "preview:b3", title = "找到 B", board = previewBoard,
            message = "先凭粗细找到琴弦，再从弦枕和圆点辨认品格。", canReplay = true)) {}
    }
}

@Preview(widthDp = 600, heightDp = 260, showBackground = true)
@Composable
private fun FretboardContractPreview() {
    GuitarLearningTheme("clear") { TeachingFretboard(previewBoard, {}, Modifier.fillMaxSize()) }
}

@Preview(widthDp = 390, heightDp = 650, showBackground = true)
@Composable
private fun HomeContractPreview() {
    GuitarLearningTheme("clear") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeContent(listOf(HomeEntryUi("intro", "吉他入门", "当前：两个音位一小步", "继续学习", "p01"),
                HomeEntryUi("board", "指板训练", "音位练习与复习"), HomeEntryUi("tree", "知识树", "3 个节点已点亮")), {}, {}, {})
        }
    }
}
