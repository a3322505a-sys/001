package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts

/** One teaching unit is one complete route. The saved ActiveTask resumes within its sequence. */
object ScalePatternLessons {
    val ids: Set<String> = MajorScalePatterns.all.map(MajorScalePatterns::nodeId).toSet()

    private fun pitch(c: Coordinate) = MusicFacts.midi(c.string, c.fret)
    private fun at(pattern: MajorScalePattern, midi: Int, near: Coordinate? = null): Coordinate = pattern.positions
        .filter { pitch(it) == midi }
        .minWithOrNull(compareBy<Coordinate> { c -> near?.let { kotlin.math.abs(c.fret - it.fret) + 2 * kotlin.math.abs(c.string - it.string) } ?: c.fret }
            .thenBy { it.string }) ?: error("Missing pitch $midi in ${pattern.id}")

    private fun route(pattern: MajorScalePattern, pitches: List<Int>): List<Coordinate> {
        var previous: Coordinate? = null
        return pitches.map { midi -> at(pattern, midi, previous).also { previous = it } }
    }

    private fun task(pattern: MajorScalePattern, key: String, title: String, explanation: String,
        route: List<Coordinate>, range: PhysicalRange = PhysicalRange(pattern.firstFret, pattern.lastFret),
        references: List<Coordinate> = emptyList()): LearningTask {
        require(route.isNotEmpty() && route.all(range::contains))
        val rules = route.map { AnswerConstraint(ConstraintKind.COORDINATE, coordinate = it) }
        val notes = route.map(::pitch)
        return LearningTask(nodeId = MajorScalePatterns.nodeId(pattern), skillId = "pattern:${pattern.id}:$key",
            direction = Direction.STRUCTURE, prompt = "C大调 · ${pattern.title} · $title",
            explanation = explanation + "\n" + route.joinToString(" → ") {
                "${MusicFacts.label(it.string, it.fret)}（${pattern.degrees[it] ?: MusicFacts.majorDegree(pitch(it), 0)}级，${it.label}）"
            }, constraint = rules.first(), range = range, completion = CompletionKind.SEQUENCE,
            sequence = rules, relation = RelationPrompt(listOf(notes.first()), notes),
            referenceCoordinates = references, tonicPitchClass = 0, tonalMode = "major")
    }

    fun tasks(pattern: MajorScalePattern): List<LearningTask> {
        val root = pattern.roots.first { c -> (1..7).all { step ->
            val offset = listOf(0, 2, 4, 5, 7, 9, 11, 12)[step]
            pattern.positions.any { pitch(it) == pitch(c) + offset }
        } }
        val octavePitches = listOf(0, 2, 4, 5, 7, 9, 11, 12).map { pitch(root) + it }
        val ascending = pattern.positions.map(::pitch).distinct().sorted().let { pitches -> route(pattern, pitches) }
        val tonicPair = listOf(root, at(pattern, pitch(root) + 12, root))
        val triad = route(pattern, listOf(60, 64, 67, 64, 60))
        val thirdRoot = if (pattern.id == "mi") 48 else 60
        val thirds = route(pattern, listOf(0, 4, 2, 5, 4, 7, 5, 9).map { thirdRoot + it })
        val motif = route(pattern, listOf(60, 62, 64, 62, 64, 65, 64, 65, 67))
        val resolve = route(pattern, listOf(2, 4, 5, 7, 9, 11, 12).map { pitch(root) + it })
        val base = listOf(
            task(pattern, "tonic", "找主音与八度主音", "先找C，再找高一个八度的C；相差12个半音。", tonicPair),
            task(pattern, "octave", "从主音走一八度", "全、全、半、全、全、全、半；从C回到高八度C。", route(pattern, octavePitches)),
            task(pattern, "range", "遍历当前指型音域", "从本图最低音走到最高音；最低音不必是主音。", ascending),
            task(pattern, "triad", "根音—三音—五音", "C–E为大三度，C–G为纯五度；依次C–E–G–E–C。", triad),
            task(pattern, "thirds", "调内三度音对", "C–E是大三度，D–F是小三度；逐对向上。", thirds),
            task(pattern, "motif", "三音级进模进", "C–D–E、D–E–F、E–F–G；三音组不是三度音程。", motif),
            task(pattern, "resolve", "换起点回主音", "从D开始，顺着本指型的调内音向上回到C。", resolve),
        )
        val next = MajorScalePatterns.all.getOrNull(MajorScalePatterns.all.indexOf(pattern) + 1) ?: return base
        val sourceRoot = pattern.roots.firstOrNull { current -> next.roots.any { pitch(it) == pitch(current) && it != current } }
            ?: pattern.roots.first()
        val nextRoot = next.roots.firstOrNull { pitch(it) == pitch(sourceRoot) && it != sourceRoot }
            ?: next.roots.first { it != sourceRoot }
        val overlap = pattern.positions.intersect(next.positions.toSet()).firstOrNull { it != sourceRoot && it != nextRoot }
        val connection = listOfNotNull(sourceRoot, overlap, nextRoot)
        return base + task(pattern, "connect", "连接相邻${next.solfegeName}指型",
            "先从本指型走向两图的重叠音，再到相邻指型的同音高主音；同调、同级数。",
            connection, PhysicalRange(minOf(pattern.firstFret, next.firstFret), maxOf(pattern.lastFret, next.lastFret)))
    }

    fun tasks(id: String) = tasks(MajorScalePatterns.forNode(id))
    fun passed(state: LearnerState, id: String): Boolean = tasks(id).all { template ->
        state.attempts.any { it.task.skillId == template.skillId && it.independent && it.completed && it.firstCorrect == true }
    }
    fun retained(state: LearnerState, id: String, day: String, since: Long): Boolean = tasks(id).all { template ->
        state.attempts.any { it.task.skillId == template.skillId && it.independent && it.firstCorrect == true && it.at > since && it.localDay == day }
    }
    fun next(state: LearnerState, id: String, source: TaskSource): LearningTask {
        val tasks = tasks(id)
        val unseen = tasks.firstOrNull { "${it.skillId}:intro" !in state.introductions }
        val candidate = unseen ?: tasks.firstOrNull { template ->
            state.attempts.none { it.task.skillId == template.skillId && it.independent && it.completed && it.firstCorrect == true }
        } ?: tasks.minBy { template -> state.attempts.lastOrNull { it.task.skillId == template.skillId }?.at ?: 0L }
        return candidate.copy(id = newId(), source = if (unseen != null) TaskSource.DEMONSTRATION else source,
            introductionId = if (unseen != null) "${candidate.skillId}:intro" else null)
    }
}
