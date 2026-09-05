package com.a3322505a.guitarlearning.learning

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.BuildConfig
import com.a3322505a.guitarlearning.MainActivity
import com.a3322505a.guitarlearning.core.MusicFacts
import com.a3322505a.guitarlearning.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LearningApp(model: TrainingViewModel) {
    val colors = LocalGuitarColors.current
    val state by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    var page by rememberSaveable { mutableStateOf("home") }
    var returnPage by rememberSaveable { mutableStateOf("home") }
    var nodeReturnPage by rememberSaveable { mutableStateOf("home") }
    val pageStates = rememberSaveableStateHolder()
    val activity = LocalContext.current as MainActivity
    DisposableEffect(activity, page == "training") {
        activity.setTrainingImmersive(page == "training")
        onDispose { activity.setTrainingImmersive(false) }
    }
    SideEffect { activity.requestedOrientation = if (page == "training") ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    val back: () -> Unit = { page = when {
        page == "training" -> returnPage
        page.startsWith("node:") -> nodeReturnPage
        else -> "home"
    } }
    BackHandler(page != "home", onBack = back)
    val start: (String) -> Unit = { id -> model.start(id) { returnPage = page; page = "training" } }
    val detail: (String) -> Unit = { id ->
        val origin = page
        model.viewNode(id) { nodeReturnPage = origin; page = "node:$id" }
    }
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("学习档案暂时无法读取，原数据已保留。")
                    Button(onClick = model::reload, enabled = !busy) { Text("重新读取") }
                }
            }
        } else if (page == "training") {
            TrainingScreen(s, busy, model, onBack = { page = returnPage }, onEnd = { model.end { page = "home" } })
        } else {
            Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != "home") TextButton(onClick = back) { Text(if (page.startsWith("node:")) "‹ 返回" else "‹ 首页") }
                    Text(if (page == "home") "吉他 · 一小步" else when {
                        page == "tree" -> "知识树"; page == "history" -> "练习历史"; page == "settings" -> "设置"
                        page.startsWith("group:") -> HomeGroup.valueOf(page.substringAfter(':')).title
                        page.startsWith("category:") -> Category.valueOf(page.substringAfter(':')).title
                        else -> "节点详情"
                    }, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
                    if (page == "home") TextButton(onClick = { page = "settings" }) { Text("设置") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                pageStates.SaveableStateProvider(page) {
                  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        page == "home" -> HomeContent(s, start, { page = "group:${it.name}" }, { page = "tree" })
                        page.startsWith("group:") -> GroupContent(s, HomeGroup.valueOf(page.substringAfter(':')), start, detail)
                        page.startsWith("category:") -> CategoryContent(s, Category.valueOf(page.substringAfter(':')), start, detail)
                        page == "tree" -> TreeContent(s, detail, { page = "history" })
                        page.startsWith("node:") -> NodeContent(s, Curriculum.node(page.substringAfter(':')), start)
                        page == "history" -> HistoryContent(s, detail)
                        page == "settings" -> SettingsContent(s, busy, model)
                    }
                    Spacer(Modifier.height(16.dp))
                  }
                }
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = model::dismissError,
        title = { Text("操作未完成") }, text = { Text(error.orEmpty()) },
        confirmButton = { TextButton(onClick = model::retry, enabled = !busy) { Text("重试") } },
        dismissButton = { TextButton(onClick = model::dismissError) { Text("关闭") } })
}

@Composable
private fun Panel(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = LocalGuitarColors.current
    Column(Modifier.fillMaxWidth().border(1.dp, colors.border, CutCornerShape(5.dp))
        .background(colors.surface, CutCornerShape(5.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 14.sp)
        content()
    }
}

// Navigation groups only: curriculum IDs, prerequisites and persisted categories stay unchanged.
private enum class HomeGroup(val title: String, val description: String, val categories: Set<Category>) {
    INTRO("吉他入门", "认识吉他 · 基础认识 · 读谱入门", setOf(Category.GUITAR, Category.SYMBOL, Category.READING)),
    FRETBOARD("指板训练", "音位练习与复习", setOf(Category.FRETBOARD)),
    ADVANCED("进阶应用", "音程、音阶与和弦 · 规划中", setOf(Category.ADVANCED)),
}

@Composable
private fun HomeContent(s: LearnerState, start: (String) -> Unit, group: (HomeGroup) -> Unit, tree: () -> Unit) {
    val current = if (s.sessionId != null) Curriculum.node(s.currentNode) else Curriculum.next(s)
    HomeGroup.entries.forEach { item ->
        val active = current?.takeIf { it.category in item.categories }
        HomeEntry(item.title, active?.let { "当前：${it.title}" } ?: item.description,
            onClick = { group(item) }, action = active?.let { { start(it.id) } },
            actionLabel = if (s.sessionId != null) "继续学习" else "开始学习")
    }
    HomeEntry("知识树", "${s.progress.count { it.value.masteredAt != null }} 个节点已点亮", onClick = tree)
}

@Composable
private fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit, action: (() -> Unit)? = null, actionLabel: String = "") {
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
private fun GroupContent(s: LearnerState, group: HomeGroup, start: (String) -> Unit, detail: (String) -> Unit) {
    val colors = LocalGuitarColors.current
    group.categories.forEach { category ->
        if (group.categories.size > 1) Text(category.title, fontWeight = FontWeight.Bold, color = colors.accent,
            modifier = Modifier.padding(top = 6.dp))
        CategoryContent(s, category, start, detail)
    }
}

@Composable
private fun CategoryContent(s: LearnerState, category: Category, start: (String) -> Unit, detail: (String) -> Unit) {
    if (category == Category.FRETBOARD) FretboardRegions(s, start, detail)
    else Curriculum.nodes.filter { it.category == category }.forEach { node ->
        NodeRow(s, node, onClick = { detail(node.id) }, start = { start(node.id) })
    }
}

@Composable
private fun FretboardRegions(s: LearnerState, start: (String) -> Unit, detail: (String) -> Unit) {
    val colors = LocalGuitarColors.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    FretboardRegion.entries.forEach { region ->
        val open = expanded == region.name
        Row(Modifier.fillMaxWidth().heightIn(min = 82.dp)
            .border(if (open) 2.dp else 1.dp, if (open) colors.accent else colors.border, CutCornerShape(5.dp))
            .background(colors.surface, CutCornerShape(5.dp))
            .clickable { expanded = if (open) null else region.name }
            .semantics { stateDescription = if (open) "已展开" else "已折叠" }
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${region.title} · ${region.rangeLabel}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
                Text(region.progressLabel(s), fontSize = 13.sp, color = colors.muted)
            }
            Text(if (open) "⌄" else "›", fontSize = 24.sp, color = colors.accent)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            region.nodes.forEach { node ->
                NodeRow(s, node, onClick = { detail(node.id) }, start = { start(node.id) })
            }
        }
    }
}

@Composable
private fun NodeRow(s: LearnerState, node: CurriculumNode, onClick: (() -> Unit)? = null, start: (() -> Unit)? = null) {
    val colors = LocalGuitarColors.current
    val status = nodeVisualState(s, node)
    val pair = when (status) {
        NodeVisualState.MASTERED, NodeVisualState.REVIEW -> colors.mastered
        NodeVisualState.AVAILABLE, NodeVisualState.CURRENT -> colors.available
        NodeVisualState.LOCKED -> colors.locked
        NodeVisualState.PLANNED -> StateColors(colors.surface, colors.muted)
    }
    val current = s.sessionId != null && s.currentNode == node.id && Curriculum.available(s, node)
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
            } else Text(if (status == NodeVisualState.MASTERED && node.id == "g00") "已认识" else status.label,
                color = pair.ink, fontSize = 12.sp)
            if (current && status != NodeVisualState.CURRENT) Text("正在复习", color = pair.ink, fontSize = 12.sp)
        }
        if (start != null && Curriculum.available(s, node)) TextButton(onClick = start,
            colors = ButtonDefaults.textButtonColors(contentColor = pair.ink), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(if (Curriculum.mastered(s, node.id)) "复习" else if (current) "继续" else "学习")
        } else if (onClick != null) Text("›", color = pair.ink, fontSize = 22.sp)
    }
}

@Composable
private fun ThemeChoice(theme: AppTheme, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
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
private fun TreeContent(s: LearnerState, detail: (String) -> Unit, history: () -> Unit) {
    val colors = LocalGuitarColors.current
    Text("绿色已掌握 · 蓝色可学习 · 灰色未解锁 · 虚线规划中", fontSize = 13.sp, color = colors.muted)
    OutlinedButton(onClick = history) { Text("查看练习历史") }
    Curriculum.nodes.forEach { node ->
        if (node.prerequisites.isNotEmpty()) Text("来自：${node.prerequisites.joinToString("、") { Curriculum.node(it).title }} ↓", fontSize = 12.sp, color = colors.muted)
        NodeRow(s, node, onClick = { detail(node.id) })
    }
}

@Composable
private fun NodeContent(s: LearnerState, node: CurriculumNode, start: (String) -> Unit) {
    val colors = LocalGuitarColors.current
    NodeRow(s, node)
    Panel("学习内容", node.description) {
        if (node.prerequisites.isNotEmpty()) Text("先修：${node.prerequisites.joinToString("、") { Curriculum.node(it).title }}")
        if (Curriculum.available(s, node)) Button(onClick = { start(node.id) }) { Text(if (Curriculum.mastered(s, node.id)) "开始复习" else "开始 / 继续学习") }
    }
    if (node.positions.isNotEmpty()) {
        Text("掌握依据", fontWeight = FontWeight.Bold)
        node.positions.forEach { c ->
            val recent = MasteryPolicy.positionEvidence(s, c).takeLast(6)
            Panel("${c.label} · ${MusicFacts.label(c.string, c.fret)}") {
                Text("有效独立回答 ${recent.size}/6，最近正确 ${recent.count { it.firstCorrect == true }}/${recent.size}")
                Text("找位置 ${recent.count { it.task.direction == Direction.NOTE_TO_POSITION }} 次 · 看位置认音 ${recent.count { it.task.direction == Direction.POSITION_TO_NOTE }} 次", fontSize = 13.sp)
                Text(if (MasteryPolicy.positionPassed(s, c)) "该音位达到初步掌握条件" else "需要两个方向都独立作答；示范、提示和预学习不用于过关。", fontSize = 12.sp, color = colors.muted)
            }
        }
    }
    val attempts = s.attempts.filter { it.task.nodeId == node.id }
    Text("记录 ${attempts.size} 次 · 提示 ${attempts.count { it.hintLevel > 0 }} 次 · 预学习 ${attempts.count { it.task.source == TaskSource.PREVIEW }} 次", fontSize = 13.sp)
    val p = s.progress[node.id]
    if (p?.retainedOn != null) Text("${p.retainedOn}有隔日独立正确记录。", fontSize = 13.sp)
    attempts.takeLast(6).asReversed().forEach { a -> Text("${formatTime(a.at)} · ${a.task.prompt}\n${attemptLabel(a)}", fontSize = 13.sp, color = colors.muted) }
}

private fun attemptLabel(a: Attempt): String = when {
    a.task.source == TaskSource.PREVIEW -> "预学习接触，不计过关"
    a.task.source == TaskSource.DEMONSTRATION -> "跟随示范"
    a.firstCorrect == false -> if (a.corrected) "首次答错，已纠正" else "首次答错"
    a.hintLevel > 0 -> "提示辅助回答"
    a.independent -> "独立正确"
    else -> "练习正确，尚未满足证据间隔"
}

@Composable
private fun HistoryContent(s: LearnerState, detail: (String) -> Unit) {
    if (s.sessions.isEmpty()) Text("开始第一课后，这里会留下真实练习记录。")
    s.sessions.asReversed().forEach { session ->
        val attempts = s.attempts.filter { it.sessionId == session.id }
        Panel(formatTime(session.startedAt), if (session.endedAt == null) "进行中 / 已暂停" else "已结束") {
            Text("完成${attempts.count { it.completed }}个任务 · 独立回答${attempts.count { it.independent }}次")
            attempts.map { it.task.nodeId }.distinct().forEach { id -> TextButton(onClick = { detail(id) }) { Text(Curriculum.node(id).title) } }
        }
    }
}

@Composable
private fun SettingsContent(s: LearnerState, busy: Boolean, model: TrainingViewModel) {
    val colors = LocalGuitarColors.current
    val notice by model.notice.collectAsState()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    Panel("外观", "配色主题") {
        AppTheme.entries.forEach { theme ->
            ThemeChoice(theme, AppTheme.fromId(s.themeId) == theme, !busy) { model.theme(theme.id) }
        }
    }
    Panel("声音") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("指板声音", Modifier.weight(1f)); Switch(s.soundEnabled, { model.sound(it) }, enabled = !busy) }
        Text("训练中方向保持稳定。界面跟随系统字号，正确、错误同时用符号区分。", fontSize = 13.sp)
    }
    Panel("学习档案", "进度保存在本机；无需注册。") {
        Button(onClick = { export.launch("guitar-learning-backup.json") }, enabled = !busy) { Text("导出备份") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text("从备份恢复") }
        if (notice != null) Text(notice.orEmpty(), color = colors.accent)
    }
    Panel("版本") { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"); Text("区域课程 · 四套配色", fontSize = 13.sp) }
    if (restoreUri != null) AlertDialog(onDismissRequest = { restoreUri = null },
        title = { Text("恢复学习档案？") }, text = { Text("当前进度将替换为备份中的进度。恢复前会在本机保留一份当前档案副本。") },
        confirmButton = { TextButton(onClick = { restoreUri?.let(model::restore); restoreUri = null }) { Text("恢复") } },
        dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("取消") } })
}

@Composable
private fun TrainingScreen(s: LearnerState, busy: Boolean, model: TrainingViewModel, onBack: () -> Unit, onEnd: () -> Unit) {
    val colors = LocalGuitarColors.current
    val foreground by model.foreground.collectAsState()
    val a = s.active
    if (a == null) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(s.endedSummary ?: "进度已保存。")
            Button(onClick = onBack) { Text("返回") }
        }
        return
    }
    val task = a.task
    var menuOpen by remember(task.id) { mutableStateOf(false) }
    val hasBoard = task.constraint.kind != ConstraintKind.SYMBOL || task.coordinate != null
    val message = trainingMessage(a)
    LaunchedEffect(task.id, a.phase, busy, foreground) {
        if (a.phase == Phase.CORRECT && !busy && foreground) { delay(if (task.guided) 1200 else 650); model.next(task.id) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val messageHeight = (maxHeight * 0.24f).coerceIn(48.dp, 88.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "暂停并返回" }) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(task.prompt, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                if (task.showTab) TabPrompt(task.coordinate!!, Modifier.width(144.dp))
                if (a.phase == Phase.CORRECTED) Button(onClick = { model.next(task.id) }, enabled = !busy) { Text("下一题") }
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "训练菜单" }) {
                        Text("⋯", fontSize = 26.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (a.phase == Phase.ANSWERING && !task.guided) DropdownMenuItem(
                            text = { Text(if (a.hintLevel == 0) "提示" else "看示范") }, enabled = !busy,
                            onClick = { menuOpen = false; model.hint() })
                        task.coordinate?.let { c -> if (s.soundEnabled) DropdownMenuItem(text = { Text("听音") },
                            onClick = { menuOpen = false; model.play(c) }) }
                        DropdownMenuItem(text = { Text("结束练习") }, enabled = !busy,
                            onClick = { menuOpen = false; onEnd() })
                    }
                }
            }
            if (message != null) {
                val wrong = a.firstCorrect == false
                Surface(Modifier.fillMaxWidth(), shape = CutCornerShape(5.dp),
                    color = if (wrong) colors.error.background else colors.available.background,
                    border = BorderStroke(1.dp, if (wrong) colors.error.ink else colors.available.ink)) {
                    Text(message, color = if (wrong) colors.error.ink else colors.available.ink, fontSize = 15.sp,
                        modifier = Modifier.heightIn(max = messageHeight).verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            if (task.options.isNotEmpty()) AnswerOptions(a, busy, model,
                Modifier.fillMaxWidth().padding(horizontal = 48.dp).align(Alignment.CenterHorizontally))
            if (hasBoard) TeachingFretboard(a,
                task.constraint.kind != ConstraintKind.SYMBOL && !busy && a.phase in listOf(Phase.ANSWERING, Phase.CORRECTING),
                { model.answer(task.id, coordinate = it) }, Modifier.fillMaxWidth().weight(1f))
            else Spacer(Modifier.weight(1f))
        }
    }
}

// One upper message area: guidance or actionable feedback, never a repeated instruction footer.
private fun trainingMessage(active: ActiveTask): String? = when {
    active.phase == Phase.CORRECTED -> "已纠正。"
    active.phase == Phase.CORRECT -> null // Board/answer marks already confirm success.
    active.feedback.isNotBlank() -> fretboardInstruction(active.feedback)
    active.task.guided -> fretboardInstruction(active.task.explanation)
    else -> null
}

@Composable
private fun AnswerOptions(active: ActiveTask, busy: Boolean, model: TrainingViewModel, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    val task = active.task
    val mistake = active.inputs.lastOrNull { it.result == ClickResult.WRONG }?.symbol
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        task.options.forEach { option ->
            val confirmed = active.phase in listOf(Phase.CORRECT, Phase.CORRECTED) && option == task.constraint.symbol
            val wrong = mistake == option && !confirmed
            val answerShown = (task.guided || active.hintLevel >= 2 || active.phase == Phase.CORRECTING || confirmed) && option == task.constraint.symbol
            val optionColors = when {
                wrong -> colors.error
                confirmed -> colors.mastered
                answerShown -> colors.available
                else -> StateColors(colors.surface, colors.ink)
            }
            OutlinedButton(onClick = { model.answer(task.id, symbol = option) },
                enabled = !busy && active.phase in listOf(Phase.ANSWERING, Phase.CORRECTING),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), shape = CutCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = optionColors.background, contentColor = optionColors.ink,
                    disabledContainerColor = optionColors.background, disabledContentColor = optionColors.ink)) {
                Text(option + if (wrong) " ×" else if (confirmed) " ✓" else "", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun TabPrompt(c: Coordinate, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    Box(modifier.height(64.dp).background(colors.surface).semantics { contentDescription = "TAB：${c.label}" }) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            (1..6).forEach { s -> val y = size.height * (s - 0.5f) / 6; drawLine(colors.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        }
        Column(Modifier.fillMaxSize()) { (1..6).forEach { s ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (s == c.string) Text(c.fret.toString(), modifier = Modifier.background(colors.surface).padding(horizontal = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } }
    }
}

private fun formatTime(at: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))

// Old saved tasks keep their identity and evidence; only obsolete drawing instructions change.
private fun fretboardInstruction(text: String): String = text
    .replace("先看弦号，再找品格。", "先凭粗细找到琴弦，再从弦枕和圆点辨认品格。")
    .replace("先看弦号和所在区域", "先看琴弦粗细、弦枕和定位圆点")
    .replace("左侧单独的一格用来表示空弦。", "点弦枕左侧的琴弦表示弹空弦。")
    .replace("空弦不按品，用最左侧0区表示。", "空弦不按品，点弦枕左侧的琴弦。")
