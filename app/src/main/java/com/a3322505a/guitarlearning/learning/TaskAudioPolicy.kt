package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.audio.*
import com.a3322505a.guitarlearning.core.MusicFacts

internal enum class AudioPurpose { AUDITION, PROMPT, FULL_DEMONSTRATION, EAR }
internal data class TaskAudio(val cues: List<PitchCue>, val purpose: AudioPurpose, val events: List<TimedPitchEvent> = emptyList())

/** Pitch/assistance policy belongs to learning, never the device or presentation layers. */
internal object TaskAudioPolicy {
    fun position(c: Coordinate) = TaskAudio(listOf(cue(listOf(MusicFacts.midi(c.string, c.fret)))), AudioPurpose.AUDITION)
    fun prompt(a: ActiveTask): TaskAudio? {
        val t = a.task
        if (t.notation?.score?.id?.startsWith("pilot-") == true) return null
        t.referenceScore?.let { return TaskAudio(emptyList(),AudioPurpose.PROMPT,it.playback()) }
        t.notation?.score?.let { score -> return if (t.guided) TaskAudio(emptyList(),AudioPurpose.FULL_DEMONSTRATION,score.playback()) else null }
        if (t.chordProgression.isNotEmpty()) return if (t.guided) TaskAudio(t.chordProgression.map { cue(it,true) },AudioPurpose.FULL_DEMONSTRATION) else null
        t.auditoryScore?.let { return auditory(t, it) }
        t.relation?.let { r ->
            if (r.ear) return relation(r, AudioPurpose.EAR)
            if (t.guided) return relation(r, AudioPurpose.FULL_DEMONSTRATION)
            return TaskAudio(listOf(cue(r.referencePitches)), AudioPurpose.PROMPT)
        }
        if (t.chord != null) return if (t.guided) shape(t.chord) else null
        if (t.completion != CompletionKind.SINGLE) {
            if (!t.guided) return t.referenceCoordinates.takeIf { it.isNotEmpty() }?.let { TaskAudio(listOf(cue(it.map(::midi))), AudioPurpose.PROMPT) }
            val pitches = t.notation?.pitches ?: (if (t.completion == CompletionKind.SET) t.requiredTargets.map(::midi)
                else t.sequence.mapNotNull { it.midi ?: it.coordinate?.let(::midi) })
            return pitches.takeIf { it.isNotEmpty() }?.let { TaskAudio(listOf(cue(it)), AudioPurpose.FULL_DEMONSTRATION) }
        }
        if (t.constraint.kind in listOf(ConstraintKind.STRING, ConstraintKind.FRET)) return null
        // No arbitrary octave for mapping or symbol-only tasks.
        val pitch = t.coordinate?.let(::midi) ?: t.constraint.coordinate?.let(::midi) ?: t.constraint.midi
            ?: t.notation?.pitches?.singleOrNull() ?: return null
        return TaskAudio(listOf(cue(listOf(pitch))), AudioPurpose.PROMPT)
    }
    fun demonstration(a: ActiveTask): TaskAudio? {
        a.task.notation?.score?.let { score -> return if (score.id.startsWith("pilot-")) null else TaskAudio(emptyList(),AudioPurpose.FULL_DEMONSTRATION,score.playback()) }
        if (a.task.creationDurations.isNotEmpty()) {
            if (a.phase !in listOf(Phase.CORRECT,Phase.CORRECTED)) return null
            var tick = 0
            val events = a.task.sequence.indices.map { i ->
                val c = a.inputs.firstOrNull { it.targetIndex == i && it.coordinate != null && it.result != ClickResult.WRONG }?.coordinate ?: return null
                val duration = a.task.creationDurations[i]
                TimedPitchEvent(tick * 300,duration * 300,listOf(midi(c))).also { tick += duration }
            }
            return TaskAudio(emptyList(),AudioPurpose.AUDITION,events)
        }
        if (a.task.chordProgression.isNotEmpty()) return TaskAudio(a.task.chordProgression.map { cue(it,true) },AudioPurpose.FULL_DEMONSTRATION)
        a.task.auditoryScore?.let { return auditory(a.task, it) }
        a.task.relation?.let { return relation(it, if (it.ear) AudioPurpose.EAR else AudioPurpose.FULL_DEMONSTRATION) }
        return a.task.chord?.takeIf { TrainingUiAdapter.chordVisible(a) }?.let(::shape)
    }
    private fun auditory(t: LearningTask, score: ShortScore): TaskAudio {
        val reference = requireNotNull(t.relation).referencePitches
        return TaskAudio(emptyList(), AudioPurpose.EAR,
            listOf(TimedPitchEvent(0,600,reference)) + score.playback().map { it.copy(onsetMs=it.onsetMs+1000) })
    }
    fun shape(shape: ChordShape) = TaskAudio(listOf(cue(shape.pitches(), true)), AudioPurpose.FULL_DEMONSTRATION)
    private fun relation(r: RelationPrompt, purpose: AudioPurpose) = TaskAudio(listOf(cue(r.referencePitches), cue(r.targetPitches, r.chord)), purpose)
    private fun cue(pitches: List<Int>, chord: Boolean = false) = PitchCue(pitches.map(::MidiPitch), if (chord) PitchPlaybackStyle.CHORD else PitchPlaybackStyle.SEQUENCE)
    private fun midi(c: Coordinate) = MusicFacts.midi(c.string, c.fret)
}

/** In-memory ownership and once-per-task autoplay. No new persisted progress fields. Main-thread confined. */
internal class TrainingAudioSession {
    private val attempted = mutableSetOf<String>()
    private var serial = 0L
    var owner: String? = null; private set
    var visible = false; private set
    var enabled = false; private set
    var requestId: String? = null; private set
    fun bind(owner: String?, visible: Boolean, enabled: Boolean): Boolean {
        val cancel = this.owner != owner || this.visible != visible || this.enabled != enabled
        this.owner = owner; this.visible = visible; this.enabled = enabled
        if (cancel) invalidate()
        return cancel
    }
    fun claimAuto(taskId: String): Boolean = visible && enabled && owner == taskId && attempted.add(taskId)
    fun markPrompt(taskId: String) { attempted.add(taskId) }
    fun begin(): String? {
        if (!visible || !enabled || owner == null) return null
        return "${owner}:${++serial}".also { requestId = it }
    }
    fun accepts(id: String) = visible && enabled && requestId == id
    fun invalidate() { requestId = null }
}
