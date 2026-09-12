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
        ChordLessons.shapes(node.id).isNotEmpty() -> ChordLessons.shapes(node.id).any { "chord:${it.id}:intro" in state.introductions }
        node.id in StructureLessons.ids -> StructureLessons.eligible(state, node.id)
        node.id in ReadingLessons.ids -> ReadingLessons.eligible(state, node.id)
        node.id == "mapping" -> state.introductions.any { it.startsWith("mapping:") }
        node.id == "tab01" -> "tab01:intro" in state.introductions
        else -> false
    }

    fun kinds(nodes: List<CurriculumNode>, state: LearnerState): List<PracticeKind> = when {
        nodes.isNotEmpty() && nodes.all { ChordLessons.shapes(it.id).isNotEmpty() } -> listOf(PracticeKind.CHORD_SHAPE)
        nodes.isNotEmpty() && nodes.all { it.positions.isNotEmpty() } -> positionKinds + if (nodes.any { it.positions.any { c -> c.fret >= 9 } }) listOf(PracticeKind.FULL_MIXED) else emptyList()
        nodes.isNotEmpty() && nodes.all { it.id in StructureLessons.ids } -> listOf(if (nodes.all { it.id.startsWith("ear-") }) PracticeKind.REFERENCE_EAR else PracticeKind.RELATIONS)
        nodes.isNotEmpty() && nodes.all { it.id in ReadingLessons.ids } -> listOf(PracticeKind.READING)
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
            PracticeKind.RELATIONS, PracticeKind.REFERENCE_EAR -> selection.nodeIds.map { StructureLessons.next(state, it, TaskSource.PRACTICE, random) }
            PracticeKind.READING -> selection.nodeIds.map { ReadingLessons.next(state, it, TaskSource.PRACTICE, random) }
            PracticeKind.FULL_MIXED -> selection.nodeIds.flatMap { id -> introducedPositions(state, Curriculum.node(id)).flatMap { c ->
                listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE).map { direction ->
                    val t = scheduler.makePosition(id, c, direction, TaskSource.PRACTICE)
                    if (direction == Direction.POSITION_TO_NOTE) t.copy(range = PhysicalRange(0, 12, setOf(c.string))) else t.copy(
                        prompt = "在第${c.string}弦找到 ${com.a3322505a.guitarlearning.core.MusicFacts.label(c.string, c.fret)}",
                        range = PhysicalRange(0, 12, setOf(c.string)), constraint = AnswerConstraint(ConstraintKind.PITCH, midi = com.a3322505a.guitarlearning.core.MusicFacts.midi(c.string, c.fret)))
                }
            } }
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
            PracticeKind.CHORD_SHAPE -> selection.nodeIds.flatMap { id -> ChordLessons.shapes(id)
                .filter { "chord:${it.id}:intro" in state.introductions }.map { ChordLessons.make(it, id, TaskSource.PRACTICE, random) } }
            PracticeKind.TAB -> TabMaterial.singlePositions(state).filter { TabMaterial.singleTaught(state, it) }
                .map { TabMaterial.single(it, TaskSource.PRACTICE) }
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
