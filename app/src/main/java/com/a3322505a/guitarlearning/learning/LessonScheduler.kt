package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

class LessonScheduler(private val random: Random = Random.Default) {
    fun next(state: LearnerState, now: Long): LearningTask {
        val node = Curriculum.node(state.currentNode)
        val source = if (state.reviewMode) TaskSource.REVIEW else TaskSource.MAIN
        return when (node.id) {
            "g00" -> guitarTask(state, source)
            "n00" -> symbolTask(state, source)
            "tab01" -> tabTask(state, source)
            "mapping" -> MappingLessons.next(state, source, random)
            else -> positionTask(state, node, source, now)
        }
    }

    private fun guitarTask(state: LearnerState, source: TaskSource): LearningTask {
        val demos = listOf(
            "strings" to guitar("strings", 1, TaskSource.DEMONSTRATION, "1弦最细、6弦最粗，从上往下编号1–6。跟着点第1弦。"),
            "frets" to guitar("frets", 1, TaskSource.DEMONSTRATION, "金属线是品丝，两条品丝之间是品格。从弦枕向右数，点第1品格。"),
            "open" to guitar("frets", 0, TaskSource.DEMONSTRATION, "0表示空弦，不按任何品。点弦枕左侧的琴弦表示弹空弦。"),
            "markers" to guitar("markers", 12, TaskSource.DEMONSTRATION, "常见单点在3、5、7、9品，12品是双点；6品通常没有圆点。"),
            "marker15" to guitar("markers", 15, TaskSource.DEMONSTRATION, "15品再次是单点。现在看的是9–15品，品号仍按整把吉他计算。"),
        )
        demos.firstOrNull { "g00:${it.first}" !in state.introductions }?.let {
            return it.second.copy(introductionId = "g00:${it.first}")
        }
        val required = listOf("strings" to listOf(1, 6, 3, 4), "frets" to listOf(1, 3, 2, 0), "markers" to listOf(5, 12, 7, 9))
        val attempts = state.attempts.filter { it.task.nodeId == "g00" && it.independent && it.firstCorrect == true }
        val category = required.firstOrNull { (group, _) -> attempts.filter { it.task.skillId.startsWith("g00:$group:") }.map { it.task.skillId }.distinct().size < 2 }
            ?: required.random(random)
        val used = attempts.map { it.task.skillId }.toSet()
        val candidates = category.second.filter { "g00:${category.first}:$it" !in used }.ifEmpty { category.second }
        val previous = state.attempts.lastOrNull()?.task?.skillId
        val target = candidates.filter { "g00:${category.first}:$it" != previous }.ifEmpty { candidates }.first()
        return guitar(category.first, target, source)
    }

    private fun guitar(group: String, target: Int, source: TaskSource, explanation: String? = null): LearningTask {
        val isString = group == "strings"
        val prompt = when (group) {
            "strings" -> "点第${target}弦"
            "markers" -> if (target == 12) "找到双点所在的第12品" else "利用圆点找到第${target}品"
            else -> if (target == 0) "找到空弦区" else "点第${target}品格"
        }
        return LearningTask(nodeId = "g00", skillId = "g00:$group:$target", prompt = prompt,
            explanation = explanation ?: when (group) {
                "strings" -> "从最细的1弦往下数，点第${target}弦的任意一处。"
                "markers" -> "${target}品是${if (target == 12) "双点" else "单点"}所在的品格。"
                else -> if (target == 0) "空弦不按品，点弦枕左侧的琴弦。" else "从弦枕向右数，第${target}格就是${target}品。"
            },
            constraint = if (isString) AnswerConstraint(ConstraintKind.STRING, string = target) else AnswerConstraint(ConstraintKind.FRET, fret = target),
            range = if (group == "markers" && target >= 9) PhysicalRange(9, 15) else if (group == "markers") PhysicalRange(1, 7) else PhysicalRange(),
            source = source, hideStringLabels = isString && source != TaskSource.DEMONSTRATION,
            hideFretLabels = !isString && source != TaskSource.DEMONSTRATION)
    }

    private fun symbolTask(state: LearnerState, source: TaskSource): LearningTask {
        val introduced = "n00:intro" in state.introductions
        val done = state.attempts.filter { it.task.nodeId == "n00" && it.independent && it.firstCorrect == true }.map { it.task.constraint.symbol }
        val lastSymbol = state.attempts.lastOrNull { it.task.nodeId == "n00" }?.task?.constraint?.symbol
        val symbol = if (!introduced) "E" else if (lastSymbol == "E") "F" else "E"
        return LearningTask(nodeId = "n00", skillId = "symbol:$symbol:recognize", prompt = "选出音名 $symbol",
            explanation = "E和F是两个音的名字；音名用字母表示。先记住眼前这两个，其余音名遇到时再认识。",
            constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = symbol), options = listOf("E", "F").shuffled(random),
            source = if (!introduced) TaskSource.DEMONSTRATION else source,
            introductionId = if (!introduced) "n00:intro" else null)
    }

    private fun tabTask(state: LearnerState, source: TaskSource): LearningTask {
        val targets = listOf(Coordinate(1, 0), Coordinate(1, 1))
        val intro = "tab01:intro" !in state.introductions
        val last = state.attempts.lastOrNull { it.task.nodeId == "tab01" }?.task?.coordinate
        val c = targets.firstOrNull { it != last } ?: targets.first()
        return LearningTask(nodeId = "tab01", skillId = "${c.id}:tab_to_position", coordinate = c,
            direction = Direction.TAB_TO_POSITION, prompt = "按 TAB 找到位置", explanation = "TAB最上方是1弦；线上的${c.fret}表示${if (c.fret == 0) "空弦" else "第${c.fret}品"}。这次要点${c.label}。",
            constraint = AnswerConstraint(ConstraintKind.COORDINATE, coordinate = c), showTab = true,
            source = if (intro) TaskSource.DEMONSTRATION else source, introductionId = if (intro) "tab01:intro" else null)
    }

    private fun positionTask(state: LearnerState, node: CurriculumNode, source: TaskSource, now: Long): LearningTask {
        node.positions.firstOrNull { "position:${it.id}" !in state.introductions }?.let {
            return makePosition(node.id, it, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                .copy(introductionId = "position:${it.id}")
        }
        val history = state.attempts
        val independent = history.filter { it.independent }
        val oldNodes = Curriculum.nodes.filter { it.positions.isNotEmpty() && it.id != node.id && it.implemented && Curriculum.mastered(state, it.id) }
        val old = oldNodes.flatMap { n -> n.positions.map { n.id to it } }
        val due = old.filter { (id, c) -> state.progress[id]?.needsReview == true || now - (independent.lastOrNull { it.task.coordinate == c }?.at ?: 0L) >= 86_400_000L }
        val normalSinceReview = history.asReversed().takeWhile { it.task.source != TaskSource.REVIEW }.count { !it.task.guided }
        if (!state.reviewMode && old.isNotEmpty() && ((due.isNotEmpty() && normalSinceReview >= 5) || random.nextDouble() < 0.2)) {
            val pick = (due.ifEmpty { old }).minBy { (_, c) -> independent.lastOrNull { it.task.coordinate == c }?.at ?: 0L }
            return balanced(state, pick.first, listOf(pick.second), TaskSource.REVIEW)
        }
        val stableEnough = node.positions.all { c -> independent.count { it.task.coordinate == c && it.firstCorrect == true } >= 2 }
        val previewNode = Curriculum.positionSuccessor(node.id)
        if (!state.reviewMode && stableEnough && previewNode != null && history.takeLast(9).none { it.task.source == TaskSource.PREVIEW } && random.nextDouble() < 0.1) {
            return makePosition(previewNode.id, previewNode.positions.first(), Direction.NOTE_TO_POSITION, TaskSource.PREVIEW)
        }
        return balanced(state, node.id, node.positions, source)
    }

    private fun balanced(state: LearnerState, node: String, positions: List<Coordinate>, source: TaskSource): LearningTask {
        val choices = positions.flatMap { c -> listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE).map { c to it } }
        val last = state.attempts.lastOrNull()?.task
        val candidates = choices.filter { it.first != last?.coordinate || it.second != last.direction }.ifEmpty { choices }
        val eligible = candidates.filter { (c, d) ->
            val previous = state.attempts.lastOrNull { it.task.coordinate == c && (it.task.direction == d || it.task.guided || it.hintLevel > 0) }
            previous == null || state.attempts.size + 1 - previous.ordinal >= 3
        }.ifEmpty { candidates }
        val pick = eligible.shuffled(random).minBy { (c, d) ->
            state.attempts.count { it.independent && it.task.coordinate == c && it.task.direction == d }
        }
        return makePosition(node, pick.first, pick.second, source)
    }

    fun makePosition(node: String, c: Coordinate, direction: Direction, source: TaskSource): LearningTask {
        val name = MusicFacts.note(c.string, c.fret)
        val reverse = direction == Direction.POSITION_TO_NOTE
        val knownOptions = (Curriculum.noteOptions(node) + name).distinct()
        return LearningTask(nodeId = node, skillId = "std:${c.id}:${direction.name.lowercase()}", coordinate = c, direction = direction,
            prompt = if (reverse) "亮起的位置是什么音名？" else "在第${c.string}弦找到 $name",
            explanation = "${c.label}是${MusicFacts.label(c.string, c.fret)}。${if (c == Coordinate(1, 1)) "E到F相邻一品，相差半音。" else if (c == Coordinate(2, 1)) "B到C相邻一品，相差半音。" else when (node) { "p04" -> "G在不同八度仍叫G；A是本课新音名。"; "p05" -> "3弦4品与2弦空弦都是B3，同音高可有不同位置。"; "p06" -> "E到F相邻一品，仍相差半音。"; else -> "先凭粗细找到琴弦，再从弦枕和圆点辨认品格。" }}",
            constraint = if (reverse) AnswerConstraint(ConstraintKind.SYMBOL, symbol = name) else AnswerConstraint(ConstraintKind.NOTE_CLASS, symbol = name),
            range = PhysicalRange(strings = setOf(c.string)), source = source,
            options = if (reverse) knownOptions.shuffled(random) else emptyList())
    }
}
