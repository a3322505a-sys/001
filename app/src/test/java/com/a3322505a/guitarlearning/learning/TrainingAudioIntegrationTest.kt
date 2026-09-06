package com.a3322505a.guitarlearning.learning

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.a3322505a.guitarlearning.audio.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class TrainingAudioIntegrationTest {
    private class MemoryRepository(var value: LearnerState) : LearningRepository {
        var fail = false
        var block: CountDownLatch? = null
        override fun load() = value
        override fun commit(previous: LearnerState, next: LearnerState): LearnerState {
            block?.await(4, TimeUnit.SECONDS)
            check(!fail) { "injected save failure" }
            return next.copy(revision = previous.revision + 1).also { value = it }
        }
        override fun restore(previous: LearnerState, backup: String) = error("unused")
    }
    private class FakeOutput : PlaybackOutput {
        val requests = mutableListOf<PlaybackRequest>()
        val callbacks = mutableMapOf<String, (PlaybackEvent) -> Unit>()
        var current: String? = null
        override fun play(request: PlaybackRequest, onEvent: (PlaybackEvent) -> Unit) {
            current = request.requestId; requests += request; callbacks[request.requestId] = onEvent
            onEvent(PlaybackEvent(request.requestId, PlaybackStatus.STARTED))
        }
        fun emit(id: String, status: PlaybackStatus) { callbacks[id]!!(PlaybackEvent(id, status, if (status == PlaybackStatus.FAILED) "injected audio failure" else null)) }
        override fun stop() { current?.let { emit(it, PlaybackStatus.CANCELLED) }; current = null }
        override fun release() = stop()
    }
    private fun drainUntil(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
        while (!check() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS); Thread.sleep(5) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("asynchronous operation timed out", check())
    }
    private fun state(task: LearningTask) = LearnerState(currentNode = task.nodeId, active = ActiveTask(task), sessionId = "s", sessions = listOf(LearningSession("s", 1)))
    private fun run(task: LearningTask, sound: Boolean = true, rendered: Boolean = true, body: (TrainingViewModel, MemoryRepository, FakeOutput) -> Unit) {
        val repo = MemoryRepository(state(task).copy(soundEnabled = sound))
        val output = FakeOutput()
        val model = TrainingViewModel(ApplicationProvider.getApplicationContext<Application>(), repo, output)
        val store = ViewModelStore().apply { put("model", model) }
        try {
            drainUntil { model.state.value != null && !model.busy.value }
            model.foreground(true); model.pageVisible("training")
            if (rendered) model.taskDisplayed(task.id)
            drainUntil { !model.busy.value && (!rendered || !sound || TaskAudioPolicy.prompt(ActiveTask(task)) == null || output.requests.isNotEmpty()) }
            body(model, repo, output)
        } finally { model.foreground(false); store.clear() }
    }
    private fun reverse() = LearningTask(id = "b3", nodeId = "p09", skillId = "b3", coordinate = Coordinate(3, 4),
        direction = Direction.POSITION_TO_NOTE, prompt = "这是什么音", explanation = "B3",
        constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = "B"), options = listOf("A", "B"))

    @Test fun autoplayWaitsForDisplayAndFourHundredMilliseconds() = run(reverse(), rendered = false) { model, _, output ->
        assertTrue(output.requests.isEmpty())
        model.taskDisplayed("b3")
        shadowOf(Looper.getMainLooper()).idleFor(399, TimeUnit.MILLISECONDS)
        assertTrue(output.requests.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        assertEquals(1, output.requests.size)
    }
    @Test fun answerAndManualPlaybackCancelPendingAutoplay() {
        run(reverse(), rendered = false) { model, _, output ->
            model.taskDisplayed("b3")
            model.answer("b3", symbol = "B")
            drainUntil { !model.busy.value }
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertTrue(output.requests.isEmpty())
        }
        run(reverse(), rendered = false) { model, _, output ->
            model.taskDisplayed("b3"); model.replay("b3")
            assertEquals(1, output.requests.size)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertEquals(1, output.requests.size)
        }
    }
    @Test fun backgroundAndSoundOffCancelPendingAutoplay() {
        run(reverse(), rendered = false) { model, _, output ->
            model.taskDisplayed("b3"); model.foreground(false)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertTrue(output.requests.isEmpty())
        }
        run(reverse(), rendered = false) { model, _, output ->
            model.taskDisplayed("b3"); model.sound(false)
            drainUntil { !model.busy.value }
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertTrue(output.requests.isEmpty())
        }
    }

    @Test fun reverseAuditionDoesNotWriteAnswersAndWorksDuringSave() = run(reverse()) { model, repo, output ->
        assertEquals(1, output.requests.size)
        model.positionTapped(PositionTapped("stale", Coordinate(3, 4)))
        assertEquals(1, output.requests.size)
        model.positionTapped(PositionTapped("b3", Coordinate(2, 0)))
        assertEquals(2, output.requests.size)
        assertTrue(model.state.value!!.attempts.isEmpty())
        val blocker = CountDownLatch(1); repo.block = blocker
        model.fingering("notes")
        assertTrue(model.busy.value)
        model.positionTapped(PositionTapped("b3", Coordinate(1, 0)))
        assertEquals(listOf(64), output.requests.last().cues.single().pitches.map { it.noteNumber })
        blocker.countDown(); drainUntil { !model.busy.value }
        assertTrue(model.state.value!!.attempts.isEmpty())
        model.foreground(false); model.foreground(true)
        assertEquals(3, output.requests.size)
    }
    @Test fun mutedTaskOnlyAutoplaysWhenEnabledAndDoesNotRetryFailures() = run(reverse(), sound = false) { model, _, output ->
        assertTrue(output.requests.isEmpty())
        model.sound(true); drainUntil { output.requests.size == 1 && !model.busy.value }
        output.emit(output.requests.last().requestId, PlaybackStatus.FAILED)
        drainUntil { model.audio.value.failed }
        model.theme("forest"); drainUntil { !model.busy.value }
        assertEquals(1, output.requests.size)
        model.retryAudio("b3")
        assertEquals(2, output.requests.size)
    }
    @Test fun cancelledOrFailedEarPlaybackAndLateCompletionsCannotGrantEvidence() = run(StructureLessons.tasks("ear-intervals").first()) { model, _, output ->
        drainUntil { output.requests.size == 1 }
        val task = model.state.value!!.active!!.task
        val first = output.requests.last().requestId
        model.answer(task.id, symbol = task.constraint.symbol)
        assertTrue(model.state.value!!.attempts.isEmpty())
        model.pageVisible("home")
        output.emit(first, PlaybackStatus.COMPLETED)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(model.state.value!!.active!!.audioReady)
        model.pageVisible("training")
        assertEquals(1, output.requests.size)
        model.replay(task.id); drainUntil { output.requests.size == 2 }
        output.emit(output.requests.last().requestId, PlaybackStatus.FAILED)
        drainUntil { model.audio.value.failed }
        assertFalse(model.state.value!!.active!!.audioReady)
        model.replay(task.id); drainUntil { output.requests.size == 3 }
        output.emit(output.requests.last().requestId, PlaybackStatus.COMPLETED)
        drainUntil { model.state.value!!.active!!.audioReady && !model.busy.value && !model.audio.value.playing }
        model.answer(task.id, symbol = task.constraint.symbol)
        drainUntil { !model.busy.value }
        assertEquals(1, model.state.value!!.attempts.size)
    }
    @Test fun fullTheoryAudioPersistsAssistanceBeforeOutputAndKeepsItAfterFailure() = run(StructureLessons.tasks("triads").first()) { model, _, output ->
        val task = model.state.value!!.active!!.task
        assertEquals(0, model.state.value!!.active!!.hintLevel)
        model.demonstrate(task.id); drainUntil { output.requests.size == 2 }
        assertTrue(model.state.value!!.active!!.hintLevel > 0)
        output.emit(output.requests.last().requestId, PlaybackStatus.FAILED)
        drainUntil { model.audio.value.failed }
        assertTrue(model.state.value!!.active!!.hintLevel > 0)
    }
    @Test fun failedAssistanceSaveCannotLeakFullAudio() = run(StructureLessons.tasks("triads").first()) { model, repo, output ->
        repo.fail = true
        model.demonstrate(model.state.value!!.active!!.task.id)
        drainUntil { model.error.value != null && !model.busy.value }
        assertEquals(1, output.requests.size) // only public reference, never the full chord
        assertEquals(0, model.state.value!!.active!!.hintLevel)
    }
}
