package com.a3322505a.guitarlearning.learning

/** Presentation grouping only: node identity, prerequisites and evidence stay in Curriculum. */
enum class FretboardRegion(val title: String, val rangeLabel: String, val nodeIds: List<String>) {
    LOW("低把位", "0–4 品", (1..9).map { "p0$it" }),
    MIDDLE("中把位", "5–8 品", listOf("middle")),
    FULL("全指板", "0–12 品", listOf("full"));

    val nodes: List<CurriculumNode> get() = nodeIds.map(Curriculum::node)
    fun progressLabel(state: LearnerState): String = if (nodes.none { it.implemented }) "规划中"
        else "${nodes.count { Curriculum.mastered(state, it.id) }}/${nodes.size} 已掌握"
}

enum class NodeVisualState(val symbol: String, val label: String) {
    MASTERED("✓", "已初步掌握"),
    AVAILABLE("▶", "可学习"),
    CURRENT("●", "正在学习"),
    LOCKED("🔒", "未解锁"),
    REVIEW("✓", "需复习"),
    PLANNED("·", "规划中"),
}

fun nodeVisualState(state: LearnerState, node: CurriculumNode): NodeVisualState = when {
    !node.implemented -> NodeVisualState.PLANNED
    Curriculum.mastered(state, node.id) && state.progress[node.id]?.needsReview == true -> NodeVisualState.REVIEW
    Curriculum.mastered(state, node.id) -> NodeVisualState.MASTERED
    !Curriculum.available(state, node) -> NodeVisualState.LOCKED
    state.sessionId != null && state.currentNode == node.id -> NodeVisualState.CURRENT
    else -> NodeVisualState.AVAILABLE
}
