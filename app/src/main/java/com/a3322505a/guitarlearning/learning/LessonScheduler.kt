package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

class LessonScheduler(private val random: Random = Random.Default) {
    fun next(state: LearnerState, now: Long): LearningTask {
        if (state.practice != null) return PracticeLessons.next(state, this, random)
        val node = Curriculum.node(state.currentNode)
        val source = if (state.reviewMode) TaskSource.REVIEW else TaskSource.MAIN
        if (node.id in StructureLessons.ids) return StructureLessons.next(state, node.id, source, random)
        return when (node.id) {
            "g00" -> guitarTask(state, source)
            "n00" -> symbolTask(state, source)
            "tab01" -> tabTask(state, source)
            "tab02", "staff", "staff02" -> ReadingLessons.next(state, node.id, source, random)
            "mapping" -> MappingLessons.next(state, source, random)
            "chord-am", "chord-g5", "chord-f" -> ChordLessons.next(state, node.id, source, random)
            else -> positionTask(state, node, source, now)
        }
    }

    private fun guitarTask(state: LearnerState, source: TaskSource): LearningTask {
        val demos = listOf(
            "strings" to guitar("strings", 1, TaskSource.DEMONSTRATION, "当前指板从上往下：1弦（最细）→ 2弦 → 3弦 → 4弦 → 5弦 → 6弦（最粗）。\n本题点最上方的第1弦。"),
            "frets" to guitar("frets", 1, TaskSource.DEMONSTRATION, "弦枕 → 第1品格 → 第2品格。\n金属线是品丝；弦枕与第1根品丝之间是第1品格，本题点这里。"),
            "open" to guitar("frets", 0, TaskSource.DEMONSTRATION, "0 → 空弦 → 不按任何品。\n本题点弦枕左侧的琴弦，表示弹空弦。"),
            "markers" to guitar("markers", 12, TaskSource.DEMONSTRATION, "3品（单点）→ 5品（单点）→ 7品（单点）→ 9品（单点）→ 12品（双点）。\n本题找双点；6品通常没有圆点。"),
            "marker15" to guitar("markers", 15, TaskSource.DEMONSTRATION, "9品（单点）→ 12品（双点）→ 15品（单点）。\n本题点15品；虽然画面从9品开始，品号仍从弦枕计算。"),
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
                "strings" -> "当前指板：最上方1弦（最细）→ 最下方6弦（最粗）。\n从上往下数到第${target}弦，点这根弦的任意一处。"
                "markers" -> "3、5、7、9品（单点）→ 12品（双点）→ 15品（单点）。\n本题找${target}品的${if (target == 12) "双点" else "单点"}，点它所在的品格。"
                else -> if (target == 0) "0 → 空弦 → 不按品。点弦枕左侧的琴弦。" else "弦枕 → 1品格 → 2品格 → …；向右每过一格，品号加1。\n本题数到第${target}格，点品格内。"
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
            explanation = "音名用字母表示：E → F，是两个不同音的名字。\n本题找字母 $symbol；先认这两个音名。",
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
            direction = Direction.TAB_TO_POSITION, prompt = "按 TAB 找到位置", explanation = LessonExplanations.tab(c),
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
        val octaveDemo = c.fret == 12 && source == TaskSource.DEMONSTRATION
        val first = if (octaveDemo || c.fret <= 4) 0 else if (c.fret <= 8) 5 else 9
        val last = if (c.fret <= 4) 4 else if (c.fret <= 8) 8 else 12
        return LearningTask(nodeId = node, skillId = "std:${c.id}:${direction.name.lowercase()}", coordinate = c, direction = direction,
            prompt = if (reverse) "亮起的位置是什么音名？" else "在第${c.string}弦找到 $name",
            explanation = LessonExplanations.position(node, c),
            constraint = if (reverse) AnswerConstraint(ConstraintKind.SYMBOL, symbol = name) else if (octaveDemo) AnswerConstraint(ConstraintKind.COORDINATE, coordinate = c) else AnswerConstraint(ConstraintKind.NOTE_CLASS, symbol = name),
            range = PhysicalRange(first, last, strings = setOf(c.string)), source = source,
            referenceCoordinates = if (octaveDemo) listOf(Coordinate(c.string, 0)) else emptyList(),
            options = if (reverse) knownOptions.shuffled(random) else emptyList())
    }
}
