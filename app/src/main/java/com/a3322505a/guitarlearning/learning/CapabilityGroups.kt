package com.a3322505a.guitarlearning.learning

data class CapabilityGroup(val title: String, val description: String, val nodeIds: List<String>, val region: FretboardRegion? = null)

/** Front-end aggregation only; all actions still resolve to an existing curriculum node. */
object CapabilityGroups {
    private val grouped = listOf(
        CapabilityGroup("吉他与音名入门", "认识琴弦、品格与音名字母。", listOf("g00", "n00")),
        CapabilityGroup("低把位音位", "认识 0–4 品的自然音，在认音与找位置之间熟练转换。", FretboardRegion.LOW.nodeIds, FretboardRegion.LOW),
        CapabilityGroup("中把位音位", "用低把位作参照，认识 5–8 品的自然音位。", FretboardRegion.MIDDLE.nodeIds, FretboardRegion.MIDDLE),
        CapabilityGroup("全指板音位", "把已学音位连接到 0–12 品。", FretboardRegion.FULL.nodeIds, FretboardRegion.FULL),
        CapabilityGroup("TAB 读谱", "按六条弦线和品号，逐音读出短句。", listOf("tab01", "tab02")),
        CapabilityGroup("五线谱读谱", "辨认实际音高，在指板找到可用位置。", listOf("staff", "staff02")),
        CapabilityGroup("音名／唱名／级数", "理解固定唱名与带调性的级数，逐步混合使用。", listOf("mapping")),
        CapabilityGroup("音阶与五种指型", "C大调五种相连的指型，再对比A小调及G/F大调。", MajorScalePatterns.all.map(MajorScalePatterns::nodeId) + ScalePatternLessons.transposed.map { it.id } + listOf("scale-major", "major-retrieval", "scale-minor", "minor-tonic")),
        CapabilityGroup("音的距离与位置关系", "从半音、全音、同音高与八度认识位置关系。", listOf("structure", "pitch-relations", "octave-build")),
        CapabilityGroup("音程", "从参照音辨认距离，再换起点构建三度和五度。", listOf("intervals", "interval-build")),
        CapabilityGroup("音阶应用 · A小调五声", "少音动机与问答的可选练习。", listOf("pentatonic-a", "motif-answer")),
        CapabilityGroup("三和弦与分解", "认识根音、三音、五音，主动构建再变换起音。", listOf("triads", "triad-build", "arpeggios", "triad-inversions")),
        CapabilityGroup("和弦形态与移动", "Am、G5与F形态；强力和弦的构成和移动。", listOf("chord-am", "chord-g5", "chord-f", "power-structure", "power-move")),
        CapabilityGroup("相同音高与换位置", "在已经学过的音位之间连接，再逐步扩大范围。", listOf("cross-position", "position-connect")),
        CapabilityGroup("变化音与换调", "先学习变化音拼写，再将熟悉材料移到G/F等调。", listOf("accidentals", "keys-g-f", "transpose-pentatonic")),
        CapabilityGroup("节奏", "拍格、时值与八分细分沿用现有专项。", listOf("pulse-basics", "eighth-basics")),
        CapabilityGroup("听辨", "从参照音比较高低、音程与和弦，再到旋律回忆。", listOf("ear-height", "ear-intervals", "ear-triads", "ear-transfer", "ear-memory")),
        CapabilityGroup("和声应用", "共同音、调内和弦、短进行与旋律落点。", listOf("voice-leading", "diatonic-chords", "progressions", "chord-landings")),
        CapabilityGroup("可选创作", "小作品与换调重做，保留为后续应用。", listOf("compose-eight", "rework-key")),
    )
    fun forNode(id: String): CapabilityGroup = grouped.firstOrNull { id in it.nodeIds } ?: Curriculum.node(id).let { CapabilityGroup(it.title, it.description, listOf(id)) }
    fun all(): List<CapabilityGroup> = Curriculum.nodes.map { forNode(it.id) }.distinctBy { it.title }
    fun next(s: LearnerState, group: CapabilityGroup): CurriculumNode =
        group.nodeIds.map(Curriculum::node).firstOrNull { it.id == s.currentNode && s.active != null && Curriculum.available(s, it) }
            ?: group.nodeIds.map(Curriculum::node).firstOrNull { Curriculum.available(s, it) && !Curriculum.mastered(s, it.id) }
            ?: group.nodeIds.map(Curriculum::node).firstOrNull { Curriculum.available(s, it) } ?: Curriculum.node(group.nodeIds.first())
}
