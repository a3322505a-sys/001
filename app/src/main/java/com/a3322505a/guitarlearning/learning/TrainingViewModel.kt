package com.a3322505a.guitarlearning.learning

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.a3322505a.guitarlearning.audio.AndroidPitchPlayer
import com.a3322505a.guitarlearning.audio.MidiPitch
import com.a3322505a.guitarlearning.audio.PitchCue
import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val player = AndroidPitchPlayer { _notice.value = "声音暂时不可用，仍可继续练习。" }
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
            } finally { _busy.value = false }
        }
    }

    private fun change(onDone: () -> Unit = {}, operation: (LearnerState) -> LearnerState) {
        if (_busy.value || _state.value == null) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val previous = requireNotNull(_state.value)
                val saved = withContext(Dispatchers.IO) {
                    val next = operation(previous)
                    if (next == previous) previous else repository.commit(previous, next)
                }
                _state.value = saved
                _error.value = null
                retryAction = null
                onDone()
            } catch (e: Exception) {
                _error.value = "本次操作未保存，进度没有前进。${e.message.orEmpty()}"
                retryAction = { change(onDone, operation) }
            } finally { _busy.value = false }
        }
    }

    fun retry() { retryAction?.invoke() }
    fun dismissError() { _error.value = null }
    fun start(nodeId: String, onDone: () -> Unit) = change(onDone) { coordinator.start(it, nodeId, System.currentTimeMillis()) }
    fun hint() = change { coordinator.hint(it) }
    fun answer(taskId: String, coordinate: Coordinate? = null, symbol: String? = null) {
        if (_busy.value) return
        if (coordinate != null) play(coordinate)
        change { if (it.active?.task?.id != taskId) it else coordinator.answer(it, coordinate, symbol, System.currentTimeMillis()) }
    }
    fun next(taskId: String) = change { coordinator.next(it, taskId, System.currentTimeMillis()) }
    fun end(onDone: () -> Unit) = change(onDone) { coordinator.end(it, System.currentTimeMillis()) }
    fun sound(enabled: Boolean) = change { it.copy(soundEnabled = enabled) }
    fun viewNode(nodeId: String, onDone: () -> Unit) = change(onDone) { state ->
        val ordinal = state.attempts.maxOfOrNull { it.ordinal } ?: 0
        state.copy(viewedPositions = state.viewedPositions + Curriculum.node(nodeId).positions.associate { it.id to ordinal })
    }
    fun clearSummary() = change { it.copy(endedSummary = null) }
    fun play(coordinate: Coordinate) {
        if (_state.value?.soundEnabled != true) return
        try { player.play(PitchCue(listOf(MidiPitch(MusicFacts.midi(coordinate.string, coordinate.fret))))) }
        catch (_: Exception) { _notice.value = "声音暂时不可用，仍可继续练习。" }
    }
    fun stopAudio() = player.stop()

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
            finally { _busy.value = false }
        }
    }

    override fun onCleared() { player.release(); db.close(); super.onCleared() }
}
