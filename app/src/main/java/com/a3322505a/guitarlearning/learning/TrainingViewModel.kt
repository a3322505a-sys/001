package com.a3322505a.guitarlearning.learning

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainingViewModel @JvmOverloads constructor(
    application: Application,
    suppliedRepository: LearningRepository? = null,
    suppliedOutput: PlaybackOutput? = null,
) : AndroidViewModel(application) {
    private val db = if (suppliedRepository == null) LearningDatabase.open(application) else null
    private val repository: LearningRepository = suppliedRepository ?: RoomLearningRepository(requireNotNull(db))
    private val coordinator = LearningCoordinator()
    private val _state = MutableStateFlow<LearnerState?>(null)
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _foreground = MutableStateFlow(false)
    val foreground = _foreground.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing = _playing.asStateFlow()
    private val player: PlaybackOutput = suppliedOutput ?: AndroidPitchPlayer(application)
    private val audioSession = TrainingAudioSession()
    private val _audio = MutableStateFlow(AudioUiState())
    val audio = _audio.asStateFlow()
    private val pilotPlayer: PlaybackOutput = AndroidPitchPlayer(application)
    private val _pilotPlaying = MutableStateFlow(false)
    val pilotPlaying = _pilotPlaying.asStateFlow()
    private val _pilotLoop = MutableStateFlow(false)
    val pilotLoop = _pilotLoop.asStateFlow()
    private val _pilotCompare = MutableStateFlow(false)
    val pilotCompare = _pilotCompare.asStateFlow()
    private val _pilotMetronome = MutableStateFlow(false)
    val pilotMetronome = _pilotMetronome.asStateFlow()
    private var pilotRequest: String? = null
    private var pilotClock: Long? = null
    private var pendingPilotMs = 0L
    private var pendingPilotPlayback: Int? = null
    private var page = "home"
    private var lastAudio: Pair<String, TaskAudio>? = null
    private var retryAction: (() -> Unit)? = null

    init { reload() }

    fun reload() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { repository.load() }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "无法读取学习档案，原数据已保留。${e.message.orEmpty()}"
                retryAction = { reload() }
            } finally { _busy.value = false; syncAudio() }
        }
    }

    private fun change(onDone: () -> Unit = {}, operation: (LearnerState) -> LearnerState) {
        if (_busy.value || _state.value == null) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val previous = requireNotNull(_state.value)
                capturePilotTime()
                val elapsed = pendingPilotMs
                val playback = pendingPilotPlayback
                val timed = previous.copy(pilot = previous.pilot?.let { it.copy(elapsedMs = it.elapsedMs + elapsed, playbackMs = playback ?: it.playbackMs) })
                val next = operation(timed)
                val saved = withContext(Dispatchers.IO) {
                    if (next == previous) previous else repository.commit(previous, next)
                }
                _state.value = saved
                pendingPilotMs = (pendingPilotMs - elapsed).coerceAtLeast(0)
                if (pendingPilotPlayback == playback) pendingPilotPlayback = null
                _error.value = null
                retryAction = null
                onDone()
            } catch (e: Exception) {
                _error.value = "本次操作未保存，进度没有前进。${e.message.orEmpty()}"
                retryAction = { change(onDone, operation) }
            } finally { _busy.value = false; syncAudio() }
        }
    }

    fun retry() { retryAction?.invoke() }
    fun dismissError() { _error.value = null }
    fun start(nodeId: String, onDone: () -> Unit) = change(onDone) { coordinator.start(it, nodeId, System.currentTimeMillis()) }
    fun region(id: String, onDone: () -> Unit) = change(onDone) { coordinator.startRegion(it, id, System.currentTimeMillis()) }
    fun practice(selection: PracticePlan, onDone: () -> Unit) = change(onDone) { coordinator.startPractice(it, selection, System.currentTimeMillis()) }
    fun hint() = change { coordinator.hint(it) }
    fun answer(taskId: String, coordinate: Coordinate? = null, symbol: String? = null) {
        if (_busy.value || !trainingVisible() || _state.value?.active?.task?.id != taskId) return
        if (_state.value?.active?.task?.relation?.ear == true && _audio.value.playing) return
        change { if (it.active?.task?.id != taskId) it else coordinator.answer(it, coordinate, symbol, System.currentTimeMillis()) }
    }
    fun next(taskId: String) { if (trainingVisible()) change { coordinator.next(it, taskId, System.currentTimeMillis()) } }
    fun end(onDone: () -> Unit) { stopAudio(); change(onDone) { coordinator.end(it, System.currentTimeMillis()) } }
    fun sound(enabled: Boolean) { if (!enabled) pausePilot(); change { it.copy(soundEnabled = enabled) } }
    fun fingering(id: String) = change { it.copy(fingeringMode = FingeringMode.fromId(id).id) }
    fun legendSeen() = change { it.copy(fingerLegendSeen = true) }
    fun viewChord(id: String) = change { it.copy(viewedSkills = it.viewedSkills + ("chord:$id" to (it.attempts.maxOfOrNull { a -> a.ordinal } ?: 0))) }
    fun playShape(shape: ChordShape) {
        if (page != "chord-examples") return
        startPlayback(TaskAudioPolicy.shape(shape))
    }
    fun physical(node: String, exercise: String, rating: String) = change { s ->
        require(PhysicalPractice.exercises(node).any { it.id == exercise } && rating in listOf("顺畅","有困难"))
        s.copy(physicalReports = s.physicalReports + PhysicalReport(node,exercise,rating,System.currentTimeMillis()))
    }
    fun theme(id: String) = change { it.copy(themeId = AppTheme.fromId(id).id) }
    fun viewNode(nodeId: String, onDone: () -> Unit) = change(onDone) { state ->
        val ordinal = state.attempts.maxOfOrNull { it.ordinal } ?: 0
        state.copy(viewedPositions = state.viewedPositions + Curriculum.node(nodeId).positions.associate { it.id to ordinal })
    }
    fun clearSummary() = change { it.copy(endedSummary = null) }
    private fun trainingVisible() = page == "training" && _foreground.value
    fun pageVisible(value: String) {
        capturePilotTime(); page = value
        if (!trainingVisible()) pausePilot()
        syncPilotClock(); syncAudio()
        if (pendingPilotMs > 0 || pendingPilotPlayback != null) flushPilotTime()
    }
    private fun syncAudio() {
        val s = _state.value
        val owner = if (page == "training") s?.active?.task?.id else if (page == "chord-examples") "chord-examples" else null
        if (audioSession.bind(owner, _foreground.value && owner != null, s?.soundEnabled == true)) {
            player.stop(); _audio.value = AudioUiState(); _playing.value = false; lastAudio = null
        }
        val active = s?.active ?: return
        if (!trainingVisible() || _busy.value || active.phase != Phase.ANSWERING) return
        val spec = TaskAudioPolicy.prompt(active) ?: return
        if (audioSession.claimAuto(active.task.id)) startPlayback(spec)
    }
    fun positionTapped(tap: PositionTapped) {
        val s = _state.value ?: return
        val a = s.active?.takeIf { it.task.id == tap.viewId && trainingVisible() } ?: return
        val board = TrainingUiAdapter.board(a, FingeringMode.fromId(s.fingeringMode), _busy.value, TrainingUiAdapter.displayLast(s))
        if (board.interaction == BoardInteraction.DISABLED || tap.coordinate !in board.interactivePositions) return
        if (a.task.relation?.ear == true) {
            if (_busy.value || _audio.value.playing || !a.audioReady || tap.coordinate !in board.answerPositions) return
            // Commit the answer before another sound can change the listening gate.
            if (board.interaction == BoardInteraction.ANSWER) answer(tap.viewId, coordinate = tap.coordinate)
            return
        }
        startPlayback(TaskAudioPolicy.position(tap.coordinate))
        if (board.interaction == BoardInteraction.ANSWER && tap.coordinate in board.answerPositions) answer(tap.viewId, coordinate = tap.coordinate)
    }
    fun replay(taskId: String) {
        val a = _state.value?.active?.takeIf { it.task.id == taskId && trainingVisible() } ?: return
        if (a.task.relation?.ear == true && (_audio.value.playing || _busy.value)) return
        val spec = TaskAudioPolicy.prompt(a) ?: return
        audioSession.markPrompt(taskId)
        startPlayback(spec)
    }
    fun demonstrate(taskId: String) {
        val a = _state.value?.active?.takeIf { it.task.id == taskId && trainingVisible() } ?: return
        if (_busy.value || a.task.relation?.ear == true && _audio.value.playing) return
        TaskAudioPolicy.demonstration(a)?.let { startPlayback(it) }
    }
    fun retryAudio(taskId: String) {
        if (!trainingVisible() || _state.value?.active?.task?.id != taskId) return
        val previous = lastAudio?.takeIf { it.first == taskId } ?: return
        if (_busy.value || _audio.value.playing) return
        startPlayback(previous.second)
    }
    private fun startPlayback(spec: TaskAudio) {
        if (!audioSession.visible || !audioSession.enabled) return
        val owner = audioSession.owner ?: return
        val id = audioSession.begin() ?: return
        player.stop()
        lastAudio = owner to spec
        _audio.value = AudioUiState(playing = true)
        _playing.value = true
        val evidence = owner != "chord-examples" && (spec.purpose == AudioPurpose.EAR ||
            spec.purpose == AudioPurpose.FULL_DEMONSTRATION && _state.value?.active?.task?.let { it.relation != null || it.notation?.score != null || it.chordProgression.isNotEmpty() } == true)
        val output: () -> Unit = {
            if (audioSession.accepts(id)) {
                try { player.play(PlaybackRequest(id, spec.cues,events = spec.events)) { event ->
                    viewModelScope.launch { handlePlayback(event, owner, evidence) }
                } } catch (e: Exception) { viewModelScope.launch { handlePlayback(PlaybackEvent(id, PlaybackStatus.FAILED, e.message), owner, evidence) } }
            }
        }
        if (!evidence) output() else viewModelScope.launch {
            _busy.first { !it }
            if (!audioSession.accepts(id)) return@launch
            // Persist assistance before any portion of the full answer can be heard.
            change(onDone = output) { if (audioSession.accepts(id)) coordinator.playbackStarted(it, owner) else it }
            _busy.first { !it }
            if (_error.value != null && audioSession.accepts(id)) {
                audioSession.invalidate(); player.stop(); _playing.value = false
                _audio.value = AudioUiState(message = "播放准备未保存，请重试。", failed = true)
            }
        }
    }
    private suspend fun handlePlayback(event: PlaybackEvent, owner: String, evidence: Boolean) {
        if (!audioSession.accepts(event.requestId)) return
        when (event.status) {
            PlaybackStatus.STARTED -> Unit
            PlaybackStatus.COMPLETED -> {
                if (evidence) {
                    _busy.first { !it }
                    if (!audioSession.accepts(event.requestId) || !trainingVisible() || _state.value?.active?.task?.id != owner) return
                    change { if (audioSession.accepts(event.requestId)) coordinator.playbackCompleted(it, owner) else it }
                    _busy.first { !it }
                }
                if (audioSession.accepts(event.requestId)) { _audio.value = AudioUiState(); _playing.value = false; audioSession.invalidate() }
            }
            PlaybackStatus.CANCELLED -> { _audio.value = AudioUiState(); _playing.value = false; audioSession.invalidate() }
            PlaybackStatus.FAILED -> {
                _audio.value = AudioUiState(message = event.detail ?: "声音暂时不可用，请重试并检查媒体音量与输出设备。", failed = true)
                _playing.value = false; audioSession.invalidate()
            }
        }
    }
    fun stopAudio() { audioSession.invalidate(); player.stop(); _audio.value = AudioUiState(); _playing.value = false }
    fun foreground(active: Boolean) {
        capturePilotTime(); _foreground.value = active
        if (!active) pausePilot()
        syncPilotClock(); syncAudio()
        if (pendingPilotMs > 0 || pendingPilotPlayback != null) flushPilotTime()
    }

    private fun capturePilotTime() {
        val now = android.os.SystemClock.elapsedRealtime()
        pilotClock?.let { pendingPilotMs += (now - it).coerceAtLeast(0) }
        pilotClock = if (trainingVisible() && _state.value?.pilot != null) now else null
    }
    private fun syncPilotClock() { pilotClock = if (trainingVisible() && _state.value?.pilot != null) android.os.SystemClock.elapsedRealtime() else null }
    private fun flushPilotTime() { viewModelScope.launch { _busy.first { !it }; if (pendingPilotMs > 0 || pendingPilotPlayback != null) change { it } } }
    fun startPilot(mode: PilotMode, onDone: () -> Unit) = change({ syncPilotClock(); onDone() }) { ShortScorePilot.begin(it,mode,System.currentTimeMillis()) }
    fun finishPilot(rating: String?, comment: String, onDone: () -> Unit) {
        pausePilot()
        change({ _pilotCompare.value = false; _pilotLoop.value = false; pilotClock = null; onDone() }) { ShortScorePilot.finish(it,System.currentTimeMillis(),rating,comment) }
    }
    fun pilotTempo(bpm: Int) { pausePilot(); change { s -> s.copy(pilot = s.pilot?.copy(bpm = bpm.coerceIn(40,80),playbackMs = 0)) } }
    fun togglePilotLoop() { _pilotLoop.value = !_pilotLoop.value }
    fun togglePilotCompare() {
        val run = _state.value?.pilot ?: return
        if (ShortScorePilot.role(run.clip) != PilotRole.PRACTICE) return
        if (_pilotCompare.value) _pilotCompare.value = false else change({ _pilotCompare.value = true }) { s -> s.copy(pilot = s.pilot?.copy(assisted = true),active = s.active?.copy(hintLevel = maxOf(1,s.active.hintLevel))) }
    }
    private fun pausePilot() {
        if (pilotRequest == null) return
        val at = if (_pilotPlaying.value) pilotPlayer.positionMs() else 0
        val wasMelody = _pilotPlaying.value
        pilotRequest = null; pilotPlayer.stop(); _pilotPlaying.value = false; _pilotMetronome.value = false
        if (wasMelody) pendingPilotPlayback = at
    }
    fun playPilot() {
        val run = _state.value?.pilot ?: return
        if (_busy.value || !trainingVisible() || !_state.value!!.soundEnabled || ShortScorePilot.role(run.clip) != PilotRole.PRACTICE) return
        if (_pilotPlaying.value) { pausePilot(); flushPilotTime(); return }
        pausePilot(); stopAudio()
        change({ outputPilot(false) }) { s -> s.copy(pilot = s.pilot?.copy(assisted = true),active = s.active?.copy(hintLevel = maxOf(1,s.active.hintLevel))) }
    }
    fun togglePilotMetronome() {
        if (_pilotMetronome.value) { pausePilot(); return }
        if (!trainingVisible() || _state.value?.soundEnabled != true || _busy.value) return
        pausePilot(); outputPilot(true)
    }
    private fun outputPilot(metronome: Boolean) {
        val run = _state.value?.pilot ?: return
        if (!trainingVisible() || _state.value?.soundEnabled != true) return
        val id = "pilot:${newId()}"; pilotRequest = id
        _pilotPlaying.value = !metronome; _pilotMetronome.value = metronome
        val beat = 60000 / run.bpm
        val events = if (metronome) (0..3).flatMap { i -> listOf(TimedPitchEvent(i*beat,50,listOf(if(i==0)85 else 80)),TimedPitchEvent(i*beat+50,beat-50,emptyList())) }
            else run.score.playback(run.bpm)
        val end = events.maxOf { it.onsetMs + it.durationMs }
        val offset = if (metronome || run.playbackMs >= end) 0 else run.playbackMs
        pilotPlayer.play(PlaybackRequest(id,emptyList(),events=events,startAtMs=offset)) { event -> viewModelScope.launch {
            if (pilotRequest != id) return@launch
            when (event.status) {
                PlaybackStatus.COMPLETED -> {
                    pilotRequest = null; _pilotPlaying.value = false
                    if (metronome) outputPilot(true) else {
                        _busy.first { !it }
                        change({ if (_pilotLoop.value) outputPilot(false) }) { s -> s.copy(pilot = s.pilot?.copy(playbackMs=0)) }
                    }
                }
                PlaybackStatus.FAILED, PlaybackStatus.CANCELLED -> { pilotRequest = null; _pilotPlaying.value = false; _pilotMetronome.value = false }
                else -> Unit
            }
        } }
    }

    fun export(uri: Uri) {
        val snapshot = _state.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("无法打开保存位置")
                    stream.bufferedWriter().use { it.write(LearningCodec.encode(snapshot)) }
                }
                _notice.value = "学习档案已导出。"
            } catch (e: Exception) { _error.value = "导出失败：${e.message}" }
        }
    }

    fun restore(uri: Uri) {
        if (_busy.value) return
        val previous = _state.value ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("无法读取文件")
                    val bytes = stream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(output.size() + n <= 20 * 1024 * 1024) { "文件过大" }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 20 * 1024 * 1024) { "文件过大" }
                    val backup = bytes.toString(Charsets.UTF_8)
                    LearningCodec.decode(backup)
                    // A reversible checkpoint is retained before replacing a valid current profile.
                    getApplication<Application>().filesDir.resolve("before-restore.json").writeText(LearningCodec.encode(previous))
                    repository.restore(previous, backup)
                }
                _state.value = restored
                _error.value = null
                _notice.value = "学习档案已恢复。"
            } catch (e: Exception) { _error.value = "恢复失败，原档案未改变。${e.message.orEmpty()}" }
            finally { _busy.value = false; syncAudio() }
        }
    }

    override fun onCleared() { pilotPlayer.release(); audioSession.invalidate(); player.release(); db?.close(); super.onCleared() }
}
