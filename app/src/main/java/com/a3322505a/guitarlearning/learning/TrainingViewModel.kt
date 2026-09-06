package com.a3322505a.guitarlearning.learning

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.audio.MidiPitch
import com.a3322505a.guitarlearning.audio.PitchCue
import com.a3322505a.guitarlearning.core.MusicFacts
import com.a3322505a.guitarlearning.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LearningDatabase.open(application)
    private val repository: LearningRepository = RoomLearningRepository(db)
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
    private val player: PlaybackOutput = AndroidPitchPlayer(application)
    private val audioSession = TrainingAudioSession()
    private val _audio = MutableStateFlow(AudioUiState())
    val audio = _audio.asStateFlow()
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
                val next = operation(previous)
                val saved = withContext(Dispatchers.IO) {
                    if (next == previous) previous else repository.commit(previous, next)
                }
                _state.value = saved
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
    fun practice(selection: PracticePlan, onDone: () -> Unit) = change(onDone) { coordinator.startPractice(it, selection, System.currentTimeMillis()) }
    fun hint() = change { coordinator.hint(it) }
    fun answer(taskId: String, coordinate: Coordinate? = null, symbol: String? = null) {
        if (_busy.value || !trainingVisible() || _state.value?.active?.task?.id != taskId) return
        if (_state.value?.active?.task?.relation?.ear == true && _audio.value.playing) return
        change { if (it.active?.task?.id != taskId) it else coordinator.answer(it, coordinate, symbol, System.currentTimeMillis()) }
    }
    fun next(taskId: String) { if (trainingVisible()) change { coordinator.next(it, taskId, System.currentTimeMillis()) } }
    fun end(onDone: () -> Unit) { stopAudio(); change(onDone) { coordinator.end(it, System.currentTimeMillis()) } }
    fun sound(enabled: Boolean) = change { it.copy(soundEnabled = enabled) }
    fun fingering(id: String) = change { it.copy(fingeringMode = FingeringMode.fromId(id).id) }
    fun legendSeen() = change { it.copy(fingerLegendSeen = true) }
    fun viewChord(id: String) = change { it.copy(viewedSkills = it.viewedSkills + ("chord:$id" to (it.attempts.maxOfOrNull { a -> a.ordinal } ?: 0))) }
    fun playShape(shape: ChordShape) {
        if (page != "chord-examples") return
        startPlayback(TaskAudioPolicy.shape(shape))
    }
    fun theme(id: String) = change { it.copy(themeId = AppTheme.fromId(id).id) }
    fun viewNode(nodeId: String, onDone: () -> Unit) = change(onDone) { state ->
        val ordinal = state.attempts.maxOfOrNull { it.ordinal } ?: 0
        state.copy(viewedPositions = state.viewedPositions + Curriculum.node(nodeId).positions.associate { it.id to ordinal })
    }
    fun clearSummary() = change { it.copy(endedSummary = null) }
    private fun trainingVisible() = page == "training" && _foreground.value
    fun pageVisible(value: String) { page = value; syncAudio() }
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
        val board = TrainingUiAdapter.board(a, FingeringMode.fromId(s.fingeringMode), _busy.value)
        if (board.interaction == BoardInteraction.DISABLED || tap.coordinate !in board.interactivePositions) return
        startPlayback(TaskAudioPolicy.position(tap.coordinate))
        if (board.interaction == BoardInteraction.ANSWER) answer(tap.viewId, coordinate = tap.coordinate)
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
            spec.purpose == AudioPurpose.FULL_DEMONSTRATION && _state.value?.active?.task?.relation != null)
        val output: () -> Unit = {
            if (audioSession.accepts(id)) {
                try { player.play(PlaybackRequest(id, spec.cues)) { event ->
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
    fun foreground(active: Boolean) { _foreground.value = active; syncAudio() }

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

    override fun onCleared() { audioSession.invalidate(); player.release(); db.close(); super.onCleared() }
}
