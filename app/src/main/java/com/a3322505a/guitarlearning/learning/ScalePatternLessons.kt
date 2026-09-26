package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts

data class TransposedPatternKey(val id: String, val title: String, val tonic: Int, val shift: Int) {
    fun spelling(midi: Int): String = when {
        tonic == 7 && midi % 12 == 6 -> "F♯"
        tonic == 5 && midi % 12 == 10 -> "B♭"
        else -> MusicFacts.noteNames[midi % 12]
    }
}

/** One teaching unit is one complete route. The saved ActiveTask resumes within its sequence. */
object ScalePatternLessons {
    val transposed = listOf(TransposedPatternKey("pattern-g-si", "G大调", 7, -5),
        TransposedPatternKey("pattern-f-si", "F大调", 5, 5))
    val ids: Set<String> = MajorScalePatterns.all.map(MajorScalePatterns::nodeId).toSet() + transposed.map { it.id }

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
            task(pattern, "octave-down", "从高八度主音下行", "从高C按相反顺序回到低C；音级不变。", route(pattern, octavePitches.reversed())),
            task(pattern, "range", "遍历当前指型音域", "从本图最低音走到最高音；最低音不必是主音。", ascending),
            task(pattern, "range-down", "下行当前指型音域", "从本图最高音走到最低音；按实际音高下降。", ascending.reversed()),
            task(pattern, "triad", "根音—三音—五音", "C–E为大三度，C–G为纯五度；依次C–E–G–E–C。", triad),
            task(pattern, "thirds", "调内三度音对", "C–E是大三度，D–F是小三度；逐对向上。", thirds),
            task(pattern, "motif", "三音级进模进", "C–D–E、D–E–F、E–F–G；三音组不是三度音程。", motif),
            task(pattern, "resolve", "换起点回主音", "从D开始，顺着本指型的调内音向上回到C。", resolve),
            task(pattern, "minor", "比较A自然小调的主音与落点", "C大调与A自然小调共用音位；这里A是1级，C是3级，E是5级。",
                route(pattern, listOf(57, 60, 64, 60, 57))).copy(
                prompt = "A自然小调 · ${pattern.title} · A–C–E–C–A",
                explanation = "A自然小调：A是1级，C是3级，E是5级；C大调里同三个音分别是6、1、3级。主音和落点改为A。",
                tonicPitchClass = 9, tonalMode = "minor"),
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

    fun tasks(id: String): List<LearningTask> = transposed.firstOrNull { it.id == id }?.let(::transposedTasks)
        ?: tasks(MajorScalePatterns.forNode(id))

    private fun transposedTasks(key: TransposedPatternKey): List<LearningTask> {
        val original = MajorScalePatterns.all.first { it.id == "si" }
        return tasks(original).filterNot { it.skillId.endsWith(":connect") || it.skillId.endsWith(":minor") }.map { source ->
            val moved = source.sequence.map { rule ->
                AnswerConstraint(ConstraintKind.COORDINATE, coordinate = requireNotNull(rule.coordinate).let { Coordinate(it.string, it.fret + key.shift) })
            }
            val pitches = moved.map { pitch(requireNotNull(it.coordinate)) }
            val notes = moved.map { requireNotNull(it.coordinate) }
            source.copy(nodeId = key.id, skillId = "${key.id}:${source.skillId.substringAfterLast(':')}",
                prompt = source.prompt.replace("C大调", key.title),
                explanation = "${key.title} · si指型：" + notes.joinToString(" → ") { c ->
                    val midi = pitch(c)
                    "${key.spelling(midi)}${midi / 12 - 1}（${checkNotNull(MusicFacts.majorDegree(midi, key.tonic))}级，${c.label}）"
                }, constraint = moved.first(), sequence = moved,
                range = PhysicalRange(original.firstFret + key.shift, original.lastFret + key.shift),
                relation = RelationPrompt(listOf(pitches.first()), pitches,
                    targetSpellings = pitches.map { "${key.spelling(it)}${it / 12 - 1}" }),
                tonicPitchClass = key.tonic)
        }
    }
    fun passed(state: LearnerState, id: String): Boolean = tasks(id).all { template ->
        state.attempts.any { it.task.skillId == template.skillId && it.independent && it.completed && it.firstCorrect == true }
    }
    fun retained(state: LearnerState, id: String, day: String, since: Long): Boolean = tasks(id).all { template ->
        state.attempts.any { it.task.skillId == template.skillId && it.independent && it.firstCorrect == true && it.at > since && it.localDay == day }
    }
    internal fun variant(task: LearningTask): LearningTask {
        val indices = when (task.skillId.substringAfterLast(':')) {
            "triad" -> listOf(0, 2, 1, 0)
            "thirds" -> listOf(6, 7, 4, 5, 2, 3, 0, 1)
            "motif" -> listOf(6, 7, 8, 3, 4, 5, 0, 1, 2)
            "resolve" -> task.sequence.indices.drop(1)
            else -> return task
        }
        val rules = indices.map(task.sequence::get)
        val relation = requireNotNull(task.relation)
        val pitches = indices.map(relation.targetPitches::get)
        val labels = relation.targetSpellings.takeIf { it.isNotEmpty() }?.let { names -> indices.map(names::get) }.orEmpty()
        val key = transposed.firstOrNull { it.id == task.nodeId }
        val explanation = "换顺序或起点，保持本组关系：" + rules.joinToString(" → ") { rule ->
            val c = requireNotNull(rule.coordinate)
            val midi = pitch(c)
            "${key?.spelling(midi) ?: MusicFacts.note(c.string, c.fret)}（${c.label}）"
        }
        return task.copy(prompt = "${task.prompt} · 变式", explanation = explanation, constraint = rules.first(),
            sequence = rules, relation = relation.copy(targetPitches = pitches, targetSpellings = labels))
    }
    fun next(state: LearnerState, id: String, source: TaskSource): LearningTask {
        val tasks = tasks(id)
        val unseen = tasks.firstOrNull { "${it.skillId}:intro" !in state.introductions }
        val candidate = unseen ?: tasks.firstOrNull { template ->
            state.attempts.none { it.task.skillId == template.skillId && it.independent && it.completed && it.firstCorrect == true }
        } ?: tasks.minBy { template -> state.attempts.lastOrNull { it.task.skillId == template.skillId }?.at ?: 0L }
        return (if (unseen != null) candidate else variant(candidate)).copy(id = newId(), source = if (unseen != null) TaskSource.DEMONSTRATION else source,
            introductionId = if (unseen != null) "${candidate.skillId}:intro" else null)
    }
}
