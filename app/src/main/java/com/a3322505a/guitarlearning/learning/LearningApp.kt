package com.a3322505a.guitarlearning.learning

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.MainActivity
import com.a3322505a.guitarlearning.ui.theme.LocalGuitarColors
import kotlinx.coroutines.delay

@Composable
fun LearningApp(model: TrainingViewModel) {
    val colors = LocalGuitarColors.current
    val state by model.state.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    var page by rememberSaveable { mutableStateOf("home") }
    var returnPage by rememberSaveable { mutableStateOf("home") }
    var nodeReturnPage by rememberSaveable { mutableStateOf("home") }
    var practiceReturnPage by rememberSaveable { mutableStateOf("home") }
    DisposableEffect(model, page) {
        model.pageVisible(page)
        onDispose { model.pageVisible("hidden") }
    }
    val pageStates = rememberSaveableStateHolder()
    val activity = LocalContext.current as MainActivity
    DisposableEffect(activity, page == "training") {
        activity.setTrainingImmersive(page == "training")
        onDispose { activity.setTrainingImmersive(false) }
    }
    SideEffect { activity.requestedOrientation = if (page == "training") ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    val back: () -> Unit = { if (page == "training" && state?.pilot == null) model.end(state?.sessionId) { page = returnPage } else page = when {
        page == "training" -> returnPage
        page.startsWith("node:") -> nodeReturnPage
        page.startsWith("practice:") -> practiceReturnPage
        else -> "home"
    } }
    BackHandler(page != "home", onBack = back)
    val start: (String) -> Unit = { id ->
        val region = if (id.startsWith("region:")) id.substringAfter(':') else RegionTraining.owner(id)?.name
        if (region != null) model.region(region) { returnPage = page; page = "training" }
        else model.start(id) { returnPage = page; page = "training" }
    }
    val resume: () -> Unit = { if (!busy) { returnPage = page; page = "training" } }
    val practice: (List<String>) -> Unit = { ids -> practiceReturnPage = page; page = "practice:${ids.joinToString(",")}" }
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
            TrainingRoute(s, busy, model, onBack = { if (s.pilot != null) page = returnPage else model.end(s.sessionId) { page = returnPage } }, onEnd = { model.end(s.sessionId) { page = returnPage } })
        } else {
            Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != "home") TextButton(onClick = back) { Text(if (page.startsWith("node:") || page.startsWith("practice:")) "‹ 返回" else "‹ 首页") }
                    Text(if (page == "home") "吉他 · 一小步" else when {
                        page.startsWith("practice:") -> "专项练习"
                        page == "score-pilot" -> "短谱试用"
                        page == "chord-examples" -> "和弦指法示例"
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
                    if (page in listOf("group:INTRO","category:READING")) OutlinedButton(onClick = { page = "score-pilot" }) { Text("短谱试用 · 8段") }
                    when {
                        page == "score-pilot" -> PilotMenu(LearningPageAdapter.pilot(s)) { mode -> model.startPilot(mode) { returnPage = "score-pilot"; page = "training" } }
                        page == "home" -> HomeContent(LearningPageAdapter.home(s), { page = if (it == "tree") "tree" else "group:$it" }, start, resume)
                        page.startsWith("group:") -> CatalogContent(LearningPageAdapter.catalog(s, HomeGroup.valueOf(page.substringAfter(':')).categories, page == "group:ADVANCED"), start, detail, practice, { page = "chord-examples" })
                        page.startsWith("category:") -> CatalogContent(LearningPageAdapter.catalog(s, setOf(Category.valueOf(page.substringAfter(':')))), start, detail, practice, { page = "chord-examples" })
                        page == "tree" -> TreeContent(LearningPageAdapter.tree(s), detail, { page = "history" })
                        page.startsWith("node:") -> NodeContent(LearningPageAdapter.node(s, Curriculum.node(page.substringAfter(':'))), start, practice) { exercise,rating -> model.physical(page.substringAfter(':'),exercise,rating) }
                        page.startsWith("practice:") -> PracticeRoute(s, page.substringAfter(':').split(','), busy) { selection ->
                            model.practice(selection) { returnPage = page; page = "training" }
                        }
                        page == "chord-examples" -> ChordExamplesRoute(s, busy, model)
                        page == "history" -> HistoryContent(LearningPageAdapter.history(s), detail)
                        page == "settings" -> SettingsRoute(s, busy, model)
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


/** Lifecycle, state collection and effects stay at this connection boundary. */
@Composable
private fun TrainingRoute(s: LearnerState, busy: Boolean, model: TrainingViewModel, onBack: () -> Unit, onEnd: () -> Unit) {
    val foreground by model.foreground.collectAsState()
    val audio by model.audio.collectAsState()
    val pilotPlaying by model.pilotPlaying.collectAsState()
    val pilotLoop by model.pilotLoop.collectAsState()
    val pilotCompare by model.pilotCompare.collectAsState()
    val pilotMetronome by model.pilotMetronome.collectAsState()
    val ui = TrainingUiAdapter.training(s, busy, audio).let { base -> base.copy(pilot = s.pilot?.let { run ->
        PilotControlsUi(run.mode,run.bpm,pilotPlaying,ShortScorePilot.role(run.clip) == PilotRole.PRACTICE,
            run.mode == PilotMode.SLOW && s.active?.phase in listOf(Phase.CORRECT,Phase.CORRECTED),pilotLoop,pilotMetronome,pilotCompare)
    }) }
    LaunchedEffect(s.sessionId, s.endedSummary, busy) {
        if (!busy && s.sessionId == null && s.endedSummary != null) onBack()
    }
    LaunchedEffect(ui.taskId, foreground, busy) {
        if (!busy && foreground && ui.taskId != null) {
            withFrameNanos { }
            model.taskDisplayed(ui.taskId)
        }
    }
    LaunchedEffect(ui.taskId, ui.autoNextDelayMs, foreground) {
        if (foreground && ui.autoNextDelayMs != null) { delay(ui.autoNextDelayMs); ui.taskId?.let(model::next) }
    }
    TrainingScreen(ui) { event ->
        val id = ui.taskId
        if (event == TrainingEvent.Back) onBack()
        else if (event == TrainingEvent.End) onEnd()
        else if (id != null && model.state.value?.active?.task?.id == id) when (event) {
            is TrainingEvent.Position -> model.positionTapped(event.tap)
            is TrainingEvent.Answer -> model.answer(id, symbol = event.symbol)
            is TrainingEvent.Fingering -> model.fingering(event.id)
            TrainingEvent.PilotPlay -> model.playPilot()
            is TrainingEvent.PilotFinish -> model.finishPilot(event.rating,event.comment,onBack)
            is TrainingEvent.PilotTempo -> model.pilotTempo(event.bpm)
            TrainingEvent.PilotLoop -> model.togglePilotLoop()
            TrainingEvent.PilotCompare -> model.togglePilotCompare()
            TrainingEvent.PilotMetronome -> model.togglePilotMetronome()
            TrainingEvent.Replay -> model.replay(id)
            TrainingEvent.RetryAudio -> model.retryAudio(id)
            TrainingEvent.Demonstrate -> model.demonstrate(id)
            TrainingEvent.Hint -> model.hint()
            TrainingEvent.Obstructed -> model.obstructed()
            TrainingEvent.Next -> model.next(id)
            TrainingEvent.OpenString -> ui.chordControls?.let { model.positionTapped(PositionTapped(id, Coordinate(it.string, 0))) }
            TrainingEvent.MuteString -> model.answer(id, symbol = "X")
            TrainingEvent.EnableSound -> model.sound(true)
            TrainingEvent.LegendSeen -> model.legendSeen()
            else -> Unit
        }
    }
}

@Composable
private fun ChordExamplesRoute(s: LearnerState, busy: Boolean, model: TrainingViewModel) {
    var shapeId by rememberSaveable { mutableStateOf(ChordShapes.am.id) }
    val audio by model.audio.collectAsState()
    LaunchedEffect(shapeId) { model.stopAudio(); model.viewChord(shapeId) }
    ChordExamples(LearningPageAdapter.examples(s, shapeId, busy, audio), { shapeId = it }, { model.playShape(ChordShapes.get(shapeId)) }, model::fingering)
}

@Composable
private fun PracticeRoute(s: LearnerState, scope: List<String>, busy: Boolean, start: (PracticePlan) -> Unit) {
    var selected by rememberSaveable(scope) { mutableStateOf<List<String>?>(null) }
    var kind by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    val ui = LearningPageAdapter.practice(s, scope, selected, kind, busy)
    PracticeContent(ui, { id, checked -> selected = if (checked) ui.selected + id else ui.selected - id }, { kind = it }, {
        ui.kind?.let { start(PracticePlan(ui.selected, PracticeKind.valueOf(it))) }
    })
}

@Composable
private fun SettingsRoute(s: LearnerState, busy: Boolean, model: TrainingViewModel) {
    val notice by model.notice.collectAsState()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    SettingsContent(LearningPageAdapter.settings(s, busy, notice), model::theme, model::fingering, model::sound,
        { export.launch("guitar-learning-backup.json") }, { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
    if (restoreUri != null) AlertDialog(onDismissRequest = { restoreUri = null },
        title = { Text("恢复学习档案？") }, text = { Text("当前进度将替换为备份中的进度。恢复前会在本机保留一份当前档案副本。") },
        confirmButton = { TextButton(onClick = { restoreUri?.let(model::restore); restoreUri = null }, enabled = !busy) { Text("恢复") } },
        dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("取消") } })
}
