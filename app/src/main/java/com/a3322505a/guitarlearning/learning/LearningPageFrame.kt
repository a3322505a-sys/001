package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pure page chrome; navigation and persistence remain in the route. */
@Composable
internal fun LearningPageFrame(
    title: String,
    backLabel: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onSettings: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.safeDrawingPadding().fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            backLabel?.let { TextButton(onClick = onBack) { Text(it) } }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            onSettings?.let { TextButton(onClick = it) { Text("设置") } }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).widthIn(max = 960.dp).fillMaxWidth()) { content() }
    }
}

@Composable
internal fun LearningPageBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ShortScoreEntry(onOpen: () -> Unit) {
    OutlinedButton(onClick = onOpen) { Text("短谱试用 · 8段") }
}

@Composable
internal fun ProfileLoading(failed: Boolean, busy: Boolean, reload: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        if (!failed) CircularProgressIndicator() else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("学习档案暂时无法读取，原数据已保留。")
            Button(onClick = reload, enabled = !busy) { Text("重新读取") }
        }
    }
}

@Composable
internal fun OperationError(message: String, busy: Boolean, retry: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("操作未完成") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = retry, enabled = !busy) { Text("重试") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}

@Composable
internal fun RestoreConfirmation(busy: Boolean, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text("恢复学习档案？") }, text = { Text("当前进度将替换为备份中的进度。恢复前会在本机保留一份当前档案副本。") },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text("恢复") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
