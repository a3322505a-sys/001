package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable data class RelationPrompt(val referencePitches: List<Int>, val targetPitches: List<Int>, val chord: Boolean = false, val ear: Boolean = false) {
    init { require(referencePitches.isNotEmpty() && targetPitches.isNotEmpty() && (referencePitches + targetPitches).all { it in 40..88 }) }
}

/** A bounded course per relationship, with stable skill identities and new task instances. */
object StructureLessons {
    val ids = listOf("structure", "pitch-relations", "intervals", "scale-major", "scale-minor", "triads", "power-structure", "cross-position", "ear-intervals", "ear-triads") + FurtherLessons.ids
    private val intervalSpellings = listOf("C4 / C4", "C4 / D♭4", "C4 / D4", "C4 / E♭4", "C4 / E4", "C4 / F4", "C4 / F♯4", "C4 / G4", "C4 / A♭4", "C4 / A4", "C4 / B♭4", "C4 / B4", "C4 / C5")
    private val templates by lazy { ids.associateWith(::buildTasks) }
    fun tasks(id: String): List<LearningTask> = templates[id].orEmpty()
    private fun key(id: String, fact: String) = "relation:$id:$fact"
    private fun midi(c: Coordinate) = MusicFacts.midi(c.string, c.fret)
    private fun locations(pitches: List<Int>, range: PhysicalRange) = pitches.mapNotNull { p -> range.positions().firstOrNull { midi(it) == p } }

    private fun choice(id: String, fact: String, prompt: String, explanation: String, answer: String, options: List<String>,
        reference: List<Int>, target: List<Int>, chord: Boolean = false, ear: Boolean = false,
        board: List<Coordinate> = emptyList(), range: PhysicalRange = PhysicalRange()) = LearningTask(
        nodeId = id, skillId = key(id, fact), direction = if (ear) Direction.REFERENCE_EAR else Direction.RELATION,
        prompt = prompt, explanation = explanation, constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = answer),
        options = options, range = range, relation = RelationPrompt(reference, target, chord, ear), referenceCoordinates = board)

    private fun intervalTasks(id: String, ear: Boolean): List<LearningTask> =
        (if (ear) listOf(0, 2, 3, 4, 5, 7, 12) else (0..12).toList()).flatMap { distance ->
            (if (distance == 0) listOf(false) else listOf(false, true)).map { down ->
                val first = if (down) 60 + distance else 60
                val second = if (down) 60 else 60 + distance
                val answer = MusicRelations.intervals[distance]
                val pool = if (ear) listOf("同度", "大二度", "小三度", "大三度", "纯四度", "纯五度", "纯八度") else MusicRelations.intervals
                val options = listOf(answer) + pool.filter { it != answer }.shuffled(Random(distance)).take(3)
                val direction = if (distance == 0) "同一高度" else if (down) "从高音向低音" else "从低音向高音"
                val range = PhysicalRange(0, 12)
                val spelled = intervalSpellings[distance].split(" / ").let { if (down) it.reversed() else it }
                choice(id, "$distance:$down",
                    if (ear) "先听参考音，再判断第二音的距离" else "${intervalSpellings[distance]} · $direction，相隔什么音程？",
                    "${spelled.joinToString(" → ")}：$direction。\n相差${distance}个半音 → $answer。" +
                        (if (distance == 6) "题面 C–F♯ 拼作增四度；相同距离拼作 C–G♭ 时是减五度，都属于三全音。" else "") +
                        (if (ear) "先听参照，再比较第二音；不用凭空猜音名。" else ""),
                    answer, options, listOf(first), listOf(second), ear = ear,
                    board = if (ear) emptyList() else locations(listOf(first, second), range), range = range)
            }
        }

    private fun buildTasks(id: String): List<LearningTask> = when (id) {
        "structure" -> listOf(Coordinate(1, 0) to Coordinate(1, 1), Coordinate(2, 0) to Coordinate(2, 1),
            Coordinate(1, 1) to Coordinate(1, 3), Coordinate(2, 1) to Coordinate(2, 3)).map { (a, b) ->
            val distance = MusicRelations.semitones(midi(a), midi(b))
            val answer = if (distance == 1) "半音" else "全音"
            choice(id, "${a.id}:${b.id}", "${a.label}到${b.label}相差多少？",
                LessonExplanations.sameString(if (a.string == 1) listOf(Coordinate(1, 0), Coordinate(1, 1), Coordinate(1, 3)) else listOf(Coordinate(2, 0), Coordinate(2, 1), Coordinate(2, 3))) + "\n本题比较 ${MusicFacts.note(a.string, a.fret)}→${MusicFacts.note(b.string, b.fret)}：$answer。",
                answer, listOf("半音", "全音"), listOf(midi(a)), listOf(midi(b)), board = listOf(a, b))
        }
        "pitch-relations" -> listOf(Coordinate(2, 0) to Coordinate(3, 4), Coordinate(1, 0) to Coordinate(4, 2),
            Coordinate(1, 0) to Coordinate(6, 0), Coordinate(1, 0) to Coordinate(1, 1)).map { (a, b) ->
            choice(id, "${a.id}:${b.id}", "${a.label}与${b.label}是什么关系？",
                "${LessonExplanations.location(a)} ↔ ${LessonExplanations.location(b)}。\n" +
                    "相差${MusicRelations.semitones(midi(a), midi(b))}个半音 → ${MusicRelations.pitchRelation(midi(a), midi(b))}。\n比较实际音高，不能只看音名字母；跨弦不能只减品号。",
                MusicRelations.pitchRelation(midi(a), midi(b)), listOf("同音高", "相差一个八度", "同音名，跨多个八度", "不同音名"),
                listOf(midi(a)), listOf(midi(b)), board = listOf(a, b))
        }
        "intervals" -> intervalTasks(id, false)
        "ear-intervals" -> intervalTasks(id, true)
        "scale-major", "scale-minor" -> {
            val major = id == "scale-major"
            val root = if (major) 60 else 57
            val offsets = if (major) MusicRelations.major else MusicRelations.naturalMinor
            val title = if (major) "C 大调" else "A 自然小调"
            val pattern = if (major) "全全半全全全半" else "全半全全半全全"
            val names = if (major) listOf("C", "D", "E", "F", "G", "A", "B", "C") else listOf("A", "B", "C", "D", "E", "F", "G", "A")
            val steps = names.zipWithNext().mapIndexed { index, (a, b) ->
                "$a→$b：${if (offsets[index + 1] - offsets[index] == 1) "半音" else "全音"}"
            }.joinToString("；")
            listOf(choice(id, "pattern", "$title 的相邻步距", "$title：${names.joinToString(" → ")}。\n$steps。\n连起来：$pattern；${names.first()}是本调主音。",
                pattern, listOf("全全半全全全半", "全半全全半全全"), listOf(root), MusicRelations.pitches(root, offsets))) +
                (1..2).map { route ->
                    val order = if (route == 1) offsets else offsets.reversed()
                    val pitches = MusicRelations.pitches(root, order)
                    val rules = pitches.map { AnswerConstraint(ConstraintKind.PITCH, midi = it) }
                    LearningTask(nodeId = id, skillId = key(id, "route:$route"), direction = Direction.STRUCTURE,
                        prompt = "$title · ${if (route == 1) "上行" else "下行"}八个音",
                        explanation = "实际音高：${pitches.joinToString(" → ") { LessonExplanations.pitch(it) }}。\n" +
                            "相邻步距：${pitches.zipWithNext().joinToString(" → ") { (a, b) -> if (MusicRelations.semitones(a, b) == 1) "半音" else "全音" }}。\n" +
                            "按这个顺序逐音定位；0–12品内同音高位置都接受。",
                        constraint = rules.first(), range = PhysicalRange(0, 12), completion = CompletionKind.SEQUENCE, sequence = rules,
                        targetSkillIds = order.map { key(id, "degree:${offsets.indexOf(it) + 1}:$route") }, relation = RelationPrompt(listOf(root), pitches))
                }
        }
        "triads", "ear-triads" -> MusicRelations.triads.flatMap { (quality, offsets) -> listOf(60, 57).map { root ->
            val rootLabel = if (root == 60) "C4" else "A3"
            val names = if (root == 60) when (quality) { "大三和弦" -> "C–E–G"; "小三和弦" -> "C–E♭–G"; "减三和弦" -> "C–E♭–G♭"; else -> "C–E–G♯" }
                else when (quality) { "大三和弦" -> "A–C♯–E"; "小三和弦" -> "A–C–E"; "减三和弦" -> "A–C–E♭"; else -> "A–C♯–E♯" }
            val ear = id == "ear-triads"
            choice(id, "$root:$quality", if (ear) "先听根音，再判断和弦性质" else "根音 $rootLabel，$names 构成什么三和弦？",
                "${names.replace("–", " → ")}：根音 → 三音 → 五音。\n" +
                    "根音→三音：${offsets[1]}个半音；三音→五音：${offsets[2] - offsets[1]}个半音 → $quality。\n" +
                    "以 $rootLabel 为0，三个音相距根音 ${offsets.joinToString(" / ")} 个半音；和弦根音不一定是曲调主音。",
                quality, MusicRelations.triads.keys.toList(), listOf(root), MusicRelations.pitches(root, offsets), chord = true, ear = ear)
        } }
        "power-structure" -> listOf(ChordShapes.g5Two, ChordShapes.g5Three).flatMap { shape ->
            val components = if (shape == ChordShapes.g5Two) "根音 / 纯五度" else "根音 / 纯五度 / 根音八度"
            val explanation = shape.sounding().joinToString(" → ") { LessonExplanations.location(it) } + "。\n" +
                "G2→D3：7个半音，是纯五度。" +
                (if (shape == ChordShapes.g5Three) "G2→G3：12个半音，是八度；多的是高八度根音。" else "这个形态只弹 G2、D3。") +
                "\n组成是$components；没有三音，所以不分大、小。"
            listOf(choice(id, "${shape.id}:quality", "${shape.title}为什么不分大、小？",
                explanation, "没有三音",
                listOf("没有三音", "多八度就是大调", "三个音必是三和弦"), listOf(43), shape.pitches(), chord = true, board = shape.sounding(), range = PhysicalRange(0, 5)),
                choice(id, "${shape.id}:members", "${shape.title}如何相对根音 G 构成？", explanation,
                    components, listOf("根音 / 纯五度", "根音 / 纯五度 / 根音八度", "根音 / 大三度 / 纯五度"),
                    listOf(43), shape.pitches(), chord = true, board = shape.sounding(), range = PhysicalRange(0, 5)))
        }
        "cross-position" -> listOf(64, 67, 59, 60, 62, 57).map { pitch ->
            val reference = locations(listOf(pitch), PhysicalRange()).first()
            val equivalents = PhysicalRange(5, 12).positions().filter { midi(it) == pitch }
            LearningTask(nodeId = id, skillId = key(id, "pitch:$pitch"), direction = Direction.RELATION,
                prompt = "跨把位找同音高：${MusicFacts.label(reference.string, reference.fret)}",
                explanation = "${LessonExplanations.location(reference)} = ${equivalents.joinToString(" = ") { LessonExplanations.location(it) }}。\n" +
                    "位置不同，实际音高都为${LessonExplanations.pitch(pitch)}，相差0个半音；在5–12品选其中任一位置。另一个八度不算同音高。",
                constraint = AnswerConstraint(ConstraintKind.PITCH, midi = pitch), range = PhysicalRange(5, 12),
                relation = RelationPrompt(listOf(pitch), listOf(pitch)))
        }
        else -> FurtherLessons.tasks(id)
    }

    fun keys(id: String): List<String> = tasks(id).flatMap { if (it.completion == CompletionKind.SEQUENCE) it.targetSkillIds else listOf(it.skillId) }.distinct()
    fun count(state: LearnerState, key: String) = state.attempts.count { it.task.skillId == key && it.independent } + MemberEvidencePolicy.evidence(state, key).size
    fun keyPassed(state: LearnerState, key: String): Boolean = MemberEvidencePolicy.skillPassed(state, key) ||
        state.attempts.filter { it.task.skillId == key && it.independent }.takeLast(3).let { it.size == 3 && it.all { a -> a.firstCorrect == true && a.completed } }
    fun passed(state: LearnerState, id: String) = keys(id).let { it.isNotEmpty() && it.all { key -> keyPassed(state, key) } }
    fun retained(state: LearnerState, id: String, day: String, since: Long) = keys(id).all { key -> state.attempts.any { a ->
        a.localDay == day && (a.task.skillId == key && a.independent && a.firstCorrect == true && a.at > since ||
            a.members.any { it.skillId == key && it.independent && it.firstCorrect && it.at > since })
    } }
    fun eligible(state: LearnerState, id: String) = tasks(id).any { "${it.skillId}:intro" in state.introductions }
    fun sameFact(a: LearningTask, b: LearningTask) = a.relation != null && b.relation != null && a.nodeId == b.nodeId &&
        a.constraint.kind == ConstraintKind.SYMBOL && a.constraint.symbol == b.constraint.symbol

    fun next(state: LearnerState, id: String, source: TaskSource, random: Random): LearningTask {
        val candidates = tasks(id).filter { source != TaskSource.PRACTICE || "${it.skillId}:intro" in state.introductions }
        require(candidates.isNotEmpty())
        val introduced = candidates.filter { "${it.skillId}:intro" in state.introductions }
        val pending = introduced.filter { t -> !(if (t.completion == CompletionKind.SEQUENCE) t.targetSkillIds.all { keyPassed(state, it) } else keyPassed(state, t.skillId)) }
        val unseen = if (source == TaskSource.PRACTICE || pending.size >= 4) null else candidates.firstOrNull { "${it.skillId}:intro" !in state.introductions }
        val previous = state.attempts.lastOrNull()?.task?.skillId
        // Already learned items fill spacing gaps when only one or two unfinished skills remain.
        val pool = introduced.filter { it.skillId != previous }.ifEmpty { introduced }
        val spaced = pool.filter { t -> t.completion == CompletionKind.SEQUENCE || state.attempts.lastOrNull {
            it.task.skillId == t.skillId || sameFact(it.task, t) && (it.task.guided || it.hintLevel > 0)
        }.let { it == null || state.attempts.size + 1 - it.ordinal >= 3 } }.ifEmpty { pool }
        val selected = unseen ?: spaced.shuffled(random).minBy { t ->
            if (t.completion == CompletionKind.SEQUENCE) t.targetSkillIds.minOf { count(state, it) } else count(state, t.skillId)
        }
        return FurtherLessons.adaptOwnWork(state, selected).copy(id = newId(), source = if (unseen != null) TaskSource.DEMONSTRATION else source,
            introductionId = if (unseen != null) "${selected.skillId}:intro" else null, options = selected.options.shuffled(random))
    }
}
