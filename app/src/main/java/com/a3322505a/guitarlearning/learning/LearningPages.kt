package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.ui.theme.*

@Composable
internal fun Panel(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = LocalGuitarColors.current
    Column(Modifier.fillMaxWidth().border(1.dp, colors.border, CutCornerShape(5.dp))
        .background(colors.surface, CutCornerShape(5.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 14.sp)
        content()
    }
}

@Composable
internal fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit, action: (() -> Unit)? = null, actionLabel: String = "") {
    val colors = LocalGuitarColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp)
        .border(1.dp, colors.border, CutCornerShape(5.dp)).background(colors.surface, CutCornerShape(5.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Text(subtitle, color = colors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) Button(onClick = action, shape = CutCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Text(actionLabel, fontSize = 13.sp)
        } else Text("›", fontSize = 24.sp, color = colors.accent)
    }
}

@Composable
internal fun NodeRow(node: NodeRowUi, onClick: (() -> Unit)? = null, start: (() -> Unit)? = null) {
    val colors = LocalGuitarColors.current
    val status = node.status
    val pair = when (status) {
        NodeVisualState.MASTERED, NodeVisualState.REVIEW -> colors.mastered
        NodeVisualState.AVAILABLE, NodeVisualState.CURRENT -> colors.available
        NodeVisualState.LOCKED -> colors.locked
        NodeVisualState.PLANNED -> StateColors(colors.surface, colors.muted)
    }
    val current = node.current
    val outline = if (status == NodeVisualState.PLANNED) Modifier.drawBehind {
        val width = 1.dp.toPx()
        drawRect(colors.border, topLeft = Offset(width / 2, width / 2),
            size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
            style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    } else Modifier.border(if (current) 3.dp else 1.dp, if (current) colors.accent else pair.ink.copy(alpha = 0.6f))
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).background(pair.background).then(outline)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(status.symbol, color = pair.ink, fontSize = 20.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(node.title, color = pair.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (status == NodeVisualState.REVIEW) {
                Text("需复习 · 已掌握", color = colors.review.ink, fontSize = 12.sp,
                    modifier = Modifier.background(colors.review.background).padding(horizontal = 6.dp, vertical = 3.dp))
            } else Text(node.statusLabel,
                color = pair.ink, fontSize = 12.sp)
            if (current && status != NodeVisualState.CURRENT) Text("正在复习", color = pair.ink, fontSize = 12.sp)
        }
        if (start != null && node.startLabel != null) TextButton(onClick = start,
            colors = ButtonDefaults.textButtonColors(contentColor = pair.ink), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(node.startLabel.orEmpty())
        } else if (onClick != null) Text("›", color = pair.ink, fontSize = 22.sp)
    }
}

@Composable
internal fun ThemeChoice(theme: AppTheme, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val preview = colorsFor(theme)
    Row(Modifier.fillMaxWidth().background(preview.background)
        .border(if (selected) 2.dp else 1.dp, if (selected) preview.accent else preview.border)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = preview.accent, unselectedColor = preview.muted,
                disabledSelectedColor = preview.accent, disabledUnselectedColor = preview.muted))
        Text(theme.title, color = preview.ink, modifier = Modifier.weight(1f))
        listOf(preview.accent, preview.mastered.background, preview.locked.background).forEach { color ->
            Box(Modifier.size(16.dp).background(color).border(1.dp, preview.border))
        }
    }
}

@Composable
internal fun HomeContent(entries: List<HomeEntryUi>, open: (String) -> Unit, start: (String) -> Unit, resume: () -> Unit) {
    entries.forEach { entry -> HomeEntry(entry.title, entry.subtitle, { open(entry.id) },
        entry.startNode?.let { id -> { if (entry.resume) resume() else start(id) } }, entry.actionLabel) }
}

@Composable
internal fun CatalogContent(state: CatalogUiState, start: (String) -> Unit, detail: (String) -> Unit, practice: (List<String>) -> Unit, examples: () -> Unit) {
    val colors = LocalGuitarColors.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    if (state.showExamples) OutlinedButton(onClick = examples) { Text("和弦指法示例") }
    state.sections.forEach { section ->
        section.title?.let { Text(it, fontWeight = FontWeight.Bold, color = colors.accent, modifier = Modifier.padding(top = 6.dp)) }
        section.rows.forEach { row -> NodeRow(row, { detail(row.id) }, { start(row.id) }) }
        section.regions.forEach { region ->
            Panel(region.title, region.progress) {
                region.note?.let { Text(it, fontSize = 13.sp) }
                region.startLabel?.let { label -> Button(onClick = { start("region:${region.id}") }) { Text(label) } }
            }
        }
    }
}

@Composable
internal fun TreeContent(rows: List<NodeRowUi>, detail: (String) -> Unit, history: () -> Unit) {
    val colors = LocalGuitarColors.current
    Text("绿色已掌握 · 蓝色可学习 · 灰色未解锁 · 虚线规划中", fontSize = 13.sp, color = colors.muted)
    OutlinedButton(onClick = history) { Text("查看练习历史") }
    rows.forEach { row ->
        if (row.prerequisites.isNotEmpty()) Text("来自：${row.prerequisites} ↓", fontSize = 12.sp, color = colors.muted)
        NodeRow(row, { detail(row.id) })
    }
}

@Composable
internal fun NodeContent(state: NodeDetailUiState, start: (String) -> Unit, practice: (List<String>) -> Unit) {
    NodeRow(state.row)
    Panel("学习内容", state.description) {
        if (state.row.prerequisites.isNotEmpty()) Text("先修：${state.row.prerequisites}")
        state.startLabel?.let { Button(onClick = { start(state.row.id) }) { Text(it) } }
    }
    if (state.canPractice) OutlinedButton(onClick = { practice(listOf(state.row.id)) }) { Text("专项练习") }
    state.panels.forEach { InfoPanel(it) {} }
    state.recordLines.forEach { Text(it, fontSize = 13.sp, color = LocalGuitarColors.current.muted) }
}

@Composable
internal fun InfoPanel(state: InfoPanelUi, detail: (String) -> Unit) {
    Panel(state.title, state.subtitle) {
        state.lines.forEach { Text(it, fontSize = 13.sp) }
        state.nodes.forEach { node -> TextButton(onClick = { detail(node.id) }) { Text(node.title) } }
    }
}

@Composable
internal fun HistoryContent(panels: List<InfoPanelUi>, detail: (String) -> Unit) {
    if (panels.isEmpty()) Text("开始第一课后，这里会留下真实练习记录。")
    panels.forEach { InfoPanel(it, detail) }
}

@Composable
internal fun SettingsContent(s: SettingsUiState, theme: (String) -> Unit, fingering: (String) -> Unit, sound: (Boolean) -> Unit, export: () -> Unit, restore: () -> Unit) {
    Panel("外观", "配色主题") {
        AppTheme.entries.forEach { item -> ThemeChoice(item, s.themeId == item.id, !s.busy) { theme(item.id) } }
    }
    Panel("指法") { FingeringSettings(s.fingeringMode, s.busy, fingering) }
    Panel("声音") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("指板声音", Modifier.weight(1f)); Switch(s.soundEnabled, sound, enabled = !s.busy) }
        Text("训练中方向保持稳定。界面跟随系统字号，正确、错误同时用符号区分。", fontSize = 13.sp)
    }
    Panel("学习档案", "进度保存在本机；无需注册。") {
        Button(onClick = export, enabled = !s.busy) { Text("导出备份") }
        OutlinedButton(onClick = restore, enabled = !s.busy) { Text("从备份恢复") }
        s.notice?.let { Text(it, color = LocalGuitarColors.current.accent) }
    }
    Panel("版本") { Text(s.version); Text("接口解耦 · 题目与指板声音", fontSize = 13.sp) }
}
