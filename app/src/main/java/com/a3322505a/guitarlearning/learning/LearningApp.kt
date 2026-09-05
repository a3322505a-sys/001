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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val state by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    var page by rememberSaveable { mutableStateOf("home") }
    var returnPage by rememberSaveable { mutableStateOf("home") }
    val activity = LocalContext.current as MainActivity
    DisposableEffect(activity, page == "training") {
        activity.setTrainingImmersive(page == "training")
        onDispose { activity.setTrainingImmersive(false) }
    }
    SideEffect { activity.requestedOrientation = if (page == "training") ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    BackHandler(page != "home") { page = if (page == "training") returnPage else "home" }
    val start: (String) -> Unit = { id -> model.start(id) { returnPage = page; page = "training" } }
    val detail: (String) -> Unit = { id -> model.viewNode(id) { page = "node:$id" } }
    Surface(Modifier.fillMaxSize(), color = PixelBackground) {
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
                    if (page != "home") TextButton(onClick = { page = "home" }) { Text("‹ 首页") }
                    Text(if (page == "home") "吉他 · 一小步" else when {
                        page == "tree" -> "知识树"; page == "history" -> "练习历史"; page == "settings" -> "设置"
                        page.startsWith("group:") -> HomeGroup.valueOf(page.substringAfter(':')).title
                        page.startsWith("category:") -> Category.valueOf(page.substringAfter(':')).title
                        else -> "节点详情"
                    }, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
                    if (page == "home") TextButton(onClick = { page = "settings" }) { Text("设置") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
    if (error != null) AlertDialog(onDismissRequest = model::dismissError,
        title = { Text("操作未完成") }, text = { Text(error.orEmpty()) },
        confirmButton = { TextButton(onClick = model::retry, enabled = !busy) { Text("重试") } },
        dismissButton = { TextButton(onClick = model::dismissError) { Text("关闭") } })
}

@Composable
private fun Panel(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().border(1.dp, PixelBorder, CutCornerShape(5.dp))
        .background(PixelSurface, CutCornerShape(5.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PixelInk)
        if (subtitle != null) Text(subtitle, color = PixelInkMuted, fontSize = 14.sp)
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
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp)
        .border(1.dp, PixelBorder, CutCornerShape(5.dp)).background(PixelSurface, CutCornerShape(5.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PixelInk)
            Text(subtitle, color = PixelInkMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) Button(onClick = action, shape = CutCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Text(actionLabel, fontSize = 13.sp)
        } else Text("›", fontSize = 24.sp, color = PixelGreenDark)
    }
}

@Composable
private fun GroupContent(s: LearnerState, group: HomeGroup, start: (String) -> Unit, detail: (String) -> Unit) {
    group.categories.forEach { category ->
        if (group.categories.size > 1) Text(category.title, fontWeight = FontWeight.Bold, color = PixelGreenDark,
            modifier = Modifier.padding(top = 6.dp))
        CategoryContent(s, category, start, detail)
    }
}

@Composable
private fun CategoryContent(s: LearnerState, category: Category, start: (String) -> Unit, detail: (String) -> Unit) {
    Curriculum.nodes.filter { it.category == category }.forEach { node ->
        Panel(node.title, "${Curriculum.status(s, node)} · ${node.description}", { detail(node.id) }) {
            if (Curriculum.available(s, node)) OutlinedButton(onClick = { start(node.id) }) {
                Text(if (Curriculum.mastered(s, node.id)) "专项复习" else "进入学习")
            }
        }
    }
}

@Composable
private fun TreeContent(s: LearnerState, detail: (String) -> Unit, history: () -> Unit) {
    Text("已掌握点亮，未掌握保持灰色。灰色节点也可以查看内容。", fontSize = 13.sp, color = PixelInkMuted)
    OutlinedButton(onClick = history) { Text("查看练习历史") }
    Curriculum.nodes.forEach { node ->
        if (node.prerequisites.isNotEmpty()) Text("来自：${node.prerequisites.joinToString("、") { Curriculum.node(it).title }} ↓", fontSize = 12.sp, color = PixelInkMuted)
        Row(Modifier.fillMaxWidth().border(if (s.currentNode == node.id) 2.dp else 1.dp, if (s.currentNode == node.id) PixelGreen else PixelBorder, CutCornerShape(4.dp))
            .background(if (Curriculum.mastered(s, node.id)) PixelGreenLight else PixelSurfaceAlt)
            .clickable { detail(node.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (Curriculum.mastered(s, node.id)) "✓" else if (Curriculum.available(s, node)) "○" else "·", fontSize = 24.sp, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) { Text(node.title, fontWeight = FontWeight.Bold); Text(Curriculum.status(s, node), fontSize = 12.sp, color = PixelInkMuted) }
            Text("›")
        }
    }
}

@Composable
private fun NodeContent(s: LearnerState, node: CurriculumNode, start: (String) -> Unit) {
    Panel(node.title, node.description) {
        Text(Curriculum.status(s, node), color = if (Curriculum.mastered(s, node.id)) PixelGreen else PixelInkMuted)
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
                Text(if (MasteryPolicy.positionPassed(s, c)) "该音位达到初步掌握条件" else "需要两个方向都独立作答；示范、提示和预学习不用于过关。", fontSize = 12.sp, color = PixelInkMuted)
            }
        }
    }
    val attempts = s.attempts.filter { it.task.nodeId == node.id }
    Text("记录 ${attempts.size} 次 · 提示 ${attempts.count { it.hintLevel > 0 }} 次 · 预学习 ${attempts.count { it.task.source == TaskSource.PREVIEW }} 次", fontSize = 13.sp)
    val p = s.progress[node.id]
    if (p?.retainedOn != null) Text("${p.retainedOn}有隔日独立正确记录。", fontSize = 13.sp)
    attempts.takeLast(6).asReversed().forEach { a -> Text("${formatTime(a.at)} · ${a.task.prompt}\n${attemptLabel(a)}", fontSize = 13.sp, color = PixelInkMuted) }
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
    val notice by model.notice.collectAsState()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    Panel("声音与显示") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("指板声音", Modifier.weight(1f)); Switch(s.soundEnabled, { model.sound(it) }, enabled = !busy) }
        Text("训练中方向保持稳定。界面跟随系统字号，正确、错误同时用符号区分。", fontSize = 13.sp)
    }
    Panel("学习档案", "进度保存在本机；无需注册。") {
        Button(onClick = { export.launch("guitar-learning-backup.json") }, enabled = !busy) { Text("导出备份") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text("从备份恢复") }
        if (notice != null) Text(notice.orEmpty(), color = PixelGreenDark)
    }
    Panel("版本") { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"); Text("课程 Draft 0.4 · 首个学习闭环", fontSize = 13.sp) }
    if (restoreUri != null) AlertDialog(onDismissRequest = { restoreUri = null },
        title = { Text("恢复学习档案？") }, text = { Text("当前进度将替换为备份中的进度。恢复前会在本机保留一份当前档案副本。") },
        confirmButton = { TextButton(onClick = { restoreUri?.let(model::restore); restoreUri = null }) { Text("恢复") } },
        dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("取消") } })
}

@Composable
private fun TrainingScreen(s: LearnerState, busy: Boolean, model: TrainingViewModel, onBack: () -> Unit, onEnd: () -> Unit) {
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
                    color = if (wrong) PixelErrorSurface else PixelGreenLight,
                    border = BorderStroke(1.dp, if (wrong) PixelError else PixelGreen)) {
                    Text(message, color = if (wrong) PixelErrorDark else PixelGreenDark, fontSize = 15.sp,
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
    val task = active.task
    val mistake = active.inputs.lastOrNull { it.result == ClickResult.WRONG }?.symbol
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        task.options.forEach { option ->
            val confirmed = active.phase in listOf(Phase.CORRECT, Phase.CORRECTED) && option == task.constraint.symbol
            val wrong = mistake == option && !confirmed
            val answerShown = (task.guided || active.hintLevel >= 2 || active.phase == Phase.CORRECTING || confirmed) && option == task.constraint.symbol
            OutlinedButton(onClick = { model.answer(task.id, symbol = option) },
                enabled = !busy && active.phase in listOf(Phase.ANSWERING, Phase.CORRECTING),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), shape = CutCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (wrong) PixelErrorSurface else if (answerShown) PixelGreenLight else Color.Transparent,
                    contentColor = if (wrong) PixelErrorDark else PixelGreenDark,
                    disabledContainerColor = if (wrong) PixelErrorSurface else if (confirmed) PixelGreenLight else Color.Transparent,
                    disabledContentColor = if (wrong) PixelErrorDark else PixelGreenDark)) {
                Text(option + if (wrong) " ×" else if (confirmed) " ✓" else "", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun TabPrompt(c: Coordinate, modifier: Modifier = Modifier) {
    Box(modifier.height(64.dp).background(PixelSurface).semantics { contentDescription = "TAB：${c.label}" }) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            (1..6).forEach { s -> val y = size.height * (s - 0.5f) / 6; drawLine(PixelBorder, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        }
        Column(Modifier.fillMaxSize()) { (1..6).forEach { s ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (s == c.string) Text(c.fret.toString(), modifier = Modifier.background(PixelSurface).padding(horizontal = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
