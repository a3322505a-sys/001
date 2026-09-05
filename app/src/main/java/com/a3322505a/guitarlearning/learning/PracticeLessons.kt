package com.a3322505a.guitarlearning.learning

import kotlin.random.Random

/** Selection and scheduling only; all answers go through the normal coordinator and repository. */
object PracticeLessons {
    val positionKinds = listOf(PracticeKind.POSITION_MIXED, PracticeKind.FIND_POSITION, PracticeKind.NAME_NOTE)
    val mappingKinds = listOf(PracticeKind.MAPPING_MIXED, PracticeKind.FIXED_MAPPING, PracticeKind.DEGREE_MAPPING)

    fun introducedPositions(state: LearnerState, node: CurriculumNode): List<Coordinate> = node.positions.filter { c ->
        "position:${c.id}" in state.introductions || state.attempts.any { it.task.coordinate == c && it.completed }
    }

    fun eligible(state: LearnerState, node: CurriculumNode): Boolean = Curriculum.available(state, node) && when {
        node.positions.isNotEmpty() -> introducedPositions(state, node).isNotEmpty()
        node.id == "mapping" -> state.introductions.any { it.startsWith("mapping:") }
        node.id == "tab01" -> "tab01:intro" in state.introductions
        else -> false
    }

    fun kinds(nodes: List<CurriculumNode>, state: LearnerState): List<PracticeKind> = when {
        nodes.all { it.positions.isNotEmpty() } -> positionKinds
        nodes.singleOrNull()?.id == "mapping" -> mappingKinds.filter { kind ->
            kind != PracticeKind.DEGREE_MAPPING || state.introductions.any { it.startsWith("mapping:major:") }
        }
        nodes.singleOrNull()?.id == "tab01" -> listOf(PracticeKind.TAB)
        else -> emptyList()
    }

    fun validateSelection(state: LearnerState, selection: PracticePlan) {
        require(selection.nodeIds.isNotEmpty() && selection.nodeIds.distinct().size == selection.nodeIds.size)
        val nodes = selection.nodeIds.map(Curriculum::node)
        require(nodes.all { eligible(state, it) }) { "请先在学习中接触这些内容。" }
        require(selection.kind in kinds(nodes, state))
    }

    fun next(state: LearnerState, scheduler: LessonScheduler, random: Random): LearningTask {
        val selection = requireNotNull(state.practice)
        val candidates = when (selection.kind) {
            in positionKinds -> selection.nodeIds.flatMap { id ->
                introducedPositions(state, Curriculum.node(id)).flatMap { c ->
                    val directions = when (selection.kind) {
                        PracticeKind.FIND_POSITION -> listOf(Direction.NOTE_TO_POSITION)
                        PracticeKind.NAME_NOTE -> listOf(Direction.POSITION_TO_NOTE)
                        else -> listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE)
                    }
                    directions.map { scheduler.makePosition(id, c, it, TaskSource.PRACTICE) }
                }
            }
            in mappingKinds -> MappingLessons.notes.flatMap { note ->
                val fixed = "mapping:fixed:$note:intro" in state.introductions && selection.kind != PracticeKind.DEGREE_MAPPING
                val degrees = "mapping:major:0:$note:intro" in state.introductions && selection.kind != PracticeKind.FIXED_MAPPING
                (if (fixed) MappingLessons.fixedDirections else emptyList())
                    .plus(if (degrees) MappingLessons.degreeDirections else emptyList())
                    .map { MappingLessons.make(note, it, TaskSource.PRACTICE) }
            }
            PracticeKind.TAB -> listOf(Coordinate(1, 0), Coordinate(1, 1)).map { c ->
                LearningTask(nodeId = "tab01", skillId = "${c.id}:tab_to_position", coordinate = c,
                    direction = Direction.TAB_TO_POSITION, prompt = "按 TAB 找到位置", explanation = "TAB 最上方是1弦，这次点${c.label}。",
                    constraint = AnswerConstraint(ConstraintKind.COORDINATE, coordinate = c), showTab = true, source = TaskSource.PRACTICE)
            }
            else -> error("没有可用的专项题型。")
        }
        require(candidates.isNotEmpty()) { "还没有接触过的内容可练习。" }
        val previous = state.attempts.lastOrNull()?.task?.skillId
        val options = candidates.filter { it.skillId != previous }.ifEmpty { candidates }
        val spaced = options.filter { task ->
            val last = state.attempts.lastOrNull { it.task.skillId == task.skillId }
            last == null || state.attempts.size + 1 - last.ordinal >= 3
        }.ifEmpty { options }
        return spaced.shuffled(random).minBy { task -> state.attempts.count { it.task.skillId == task.skillId && it.independent } }
            .let { it.copy(options = it.options.shuffled(random)) }
    }
}
