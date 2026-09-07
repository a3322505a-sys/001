package com.a3322505a.guitarlearning.learning

data class CapabilityGroup(val title: String, val description: String, val nodeIds: List<String>, val region: FretboardRegion? = null)

/** Front-end aggregation only; all actions still resolve to an existing curriculum node. */
object CapabilityGroups {
    private val grouped = listOf(
        CapabilityGroup("低把位音位", "认识 0–4 品的自然音，在认音与找位置之间熟练转换。", FretboardRegion.LOW.nodeIds, FretboardRegion.LOW),
        CapabilityGroup("中把位音位", "用低把位作参照，认识 5–8 品的自然音位。", FretboardRegion.MIDDLE.nodeIds, FretboardRegion.MIDDLE),
        CapabilityGroup("全指板音位", "把已学音位连接到 0–12 品。", FretboardRegion.FULL.nodeIds, FretboardRegion.FULL),
        CapabilityGroup("TAB 读谱", "按六条弦线和品号，逐音读出短句。", listOf("tab01", "tab02")),
        CapabilityGroup("五线谱读谱", "辨认实际音高，在指板找到可用位置。", listOf("staff", "staff02")),
        CapabilityGroup("音名／唱名／级数", "理解固定唱名与带调性的级数，逐步混合使用。", listOf("mapping")),
    )
    fun forNode(id: String): CapabilityGroup = grouped.firstOrNull { id in it.nodeIds } ?: Curriculum.node(id).let { CapabilityGroup(it.title, it.description, listOf(id)) }
    fun all(): List<CapabilityGroup> = Curriculum.nodes.map { forNode(it.id) }.distinctBy { it.title }
    fun next(s: LearnerState, group: CapabilityGroup): CurriculumNode =
        group.nodeIds.map(Curriculum::node).firstOrNull { it.id == s.currentNode && s.active != null && Curriculum.available(s, it) }
            ?: group.nodeIds.map(Curriculum::node).firstOrNull { Curriculum.available(s, it) && !Curriculum.mastered(s, it.id) }
            ?: group.nodeIds.map(Curriculum::node).firstOrNull { Curriculum.available(s, it) } ?: Curriculum.node(group.nodeIds.first())
}
