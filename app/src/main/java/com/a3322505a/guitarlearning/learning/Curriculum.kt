package com.a3322505a.guitarlearning.learning

object Curriculum {
    val nodes = listOf(
        CurriculumNode("g00", "认识吉他", Category.GUITAR, "认弦、认品格，利用常见定位圆点找位置。"),
        CurriculumNode("n00", "认识音名 E / F", Category.SYMBOL, "音名是音的名字。先认识眼前用得到的 E 和 F。", listOf("g00")),
        CurriculumNode("p01", "1弦 E / F", Category.FRETBOARD, "1弦空弦是 E4，1品是 F4；相邻一品相差半音。", listOf("n00"), listOf(Coordinate(1, 0), Coordinate(1, 1))),
        CurriculumNode("tab01", "认识 TAB", Category.READING, "六条线表示弦，数字表示品；0 表示空弦。", listOf("p01")),
        CurriculumNode("p02", "加入 G / B", Category.FRETBOARD, "1弦3品 G4、2弦空弦 B3，同时复习 E / F。", listOf("p01"), listOf(Coordinate(1, 3), Coordinate(2, 0))),
        CurriculumNode("p03", "2弦 C / D", Category.FRETBOARD, "2弦1品 C4、3品 D4；B 到 C 半音，C 到 D 全音。", listOf("p02"), listOf(Coordinate(2, 1), Coordinate(2, 3))),
        CurriculumNode("p04", "3弦 G / A", Category.FRETBOARD, "继续扩展第一把位。", listOf("p03"), listOf(Coordinate(3, 0), Coordinate(3, 2))),
        CurriculumNode("p05", "跨弦 B / D", Category.FRETBOARD, "认识同音高的不同位置。", listOf("p04"), listOf(Coordinate(3, 4), Coordinate(4, 0))),
        CurriculumNode("p06", "4弦 E / F", Category.FRETBOARD, "用熟悉的半音关系认识新位置。", listOf("p05"), listOf(Coordinate(4, 2), Coordinate(4, 3))),
        CurriculumNode("p07", "5弦 A / B", Category.FRETBOARD, "增加第五根弦。", listOf("p06"), listOf(Coordinate(5, 0), Coordinate(5, 2))),
        CurriculumNode("p08", "加入 C / E", Category.FRETBOARD, "连接第5弦与第6弦。", listOf("p07"), listOf(Coordinate(5, 3), Coordinate(6, 0))),
        CurriculumNode("p09", "6弦 F / G", Category.FRETBOARD, "完成第一把位18个自然音位置。", listOf("p08"), listOf(Coordinate(6, 1), Coordinate(6, 3))),
        // V2 mapping keeps its P03 prerequisite and its own directional/key evidence.
        CurriculumNode("mapping", "唱名与级数", Category.SYMBOL, "固定唱名、带调性语境的级数映射。", listOf("p03")),
        CurriculumNode("tab02", "TAB 短句", Category.READING, "按从左到右的顺序读指定弦品；短句不预设节奏。", listOf("tab01", "p03")),
        CurriculumNode("staff", "五线谱入门", Category.READING, "高音谱号与吉他记谱八度，再读单音；按实际音高接受等价位置。", listOf("tab01", "p03")),
        CurriculumNode("staff02", "五线谱短句", Category.READING, "依次定位短句中的音高，不限定唯一指法。", listOf("staff")),
        CurriculumNode("middle", "5品 A / E", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("p09"), listOf(Coordinate(1, 5), Coordinate(2, 5))),
        CurriculumNode("m02", "高音 B / C", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("middle"), listOf(Coordinate(1, 7), Coordinate(1, 8))),
        CurriculumNode("m03", "2弦 F / G", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m02"), listOf(Coordinate(2, 6), Coordinate(2, 8))),
        CurriculumNode("m04", "3弦 C / D", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m03"), listOf(Coordinate(3, 5), Coordinate(3, 7))),
        CurriculumNode("m05", "4弦 G / A", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m04"), listOf(Coordinate(4, 5), Coordinate(4, 7))),
        CurriculumNode("m06", "5弦 D / E", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m05"), listOf(Coordinate(5, 5), Coordinate(5, 7))),
        CurriculumNode("m07", "跨弦 F / A", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m06"), listOf(Coordinate(5, 8), Coordinate(6, 5))),
        CurriculumNode("m08", "6弦 B / C", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("m07"), listOf(Coordinate(6, 7), Coordinate(6, 8))),
        CurriculumNode("full", "12品 E / B", Category.FRETBOARD, "空弦到12品升高一个八度；逐弦认识12品锚点。", listOf("m08"), listOf(Coordinate(1, 12), Coordinate(2, 12))),
        CurriculumNode("h02", "12品 G / D", Category.FRETBOARD, "空弦到12品升高一个八度；逐弦认识12品锚点。", listOf("full"), listOf(Coordinate(3, 12), Coordinate(4, 12))),
        CurriculumNode("h03", "12品 A / E", Category.FRETBOARD, "空弦到12品升高一个八度；逐弦认识12品锚点。", listOf("h02"), listOf(Coordinate(5, 12), Coordinate(6, 12))),
        CurriculumNode("h04", "10品 D / A", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("h03"), listOf(Coordinate(1, 10), Coordinate(2, 10))),
        CurriculumNode("h05", "3弦 E / F", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("h04"), listOf(Coordinate(3, 9), Coordinate(3, 10))),
        CurriculumNode("h06", "4弦 B / C", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("h05"), listOf(Coordinate(4, 9), Coordinate(4, 10))),
        CurriculumNode("h07", "10品 G / D", Category.FRETBOARD, "利用已学锚点逐步扩展，每次学习两个新音位。", listOf("h06"), listOf(Coordinate(5, 10), Coordinate(6, 10))),
        CurriculumNode("chord-am", "Am 开放和弦形态", Category.ADVANCED, "学习推荐形态、开放弦与不弹弦；手机逐点定位不代表实琴按奏能力。", listOf("p07")),
        CurriculumNode("chord-g5", "G5 两音与三音形态", Category.ADVANCED, "根音加五音，再加根音高八度；不含大小三度。", listOf("p09")),
        CurriculumNode("chord-f", "F 横按形态", Category.ADVANCED, "区分食指覆盖与各弦最终发音，逐点设置六根弦。", listOf("chord-am", "chord-g5")),
        CurriculumNode("structure", "音程、音阶与和弦", Category.ADVANCED, "关系、结构与带参照的听觉练习。", listOf("p03"), implemented = false),
    )
    fun positionSuccessor(id: String): CurriculumNode? = nodes.firstOrNull {
        it.category == Category.FRETBOARD && it.positions.isNotEmpty() && it.implemented && id in it.prerequisites
    }
    fun noteOptions(id: String): List<String> = nodes.take(nodes.indexOfFirst { it.id == id } + 1)
        .flatMap { it.positions }.map { com.a3322505a.guitarlearning.core.MusicFacts.note(it.string, it.fret) }.distinct()
    fun node(id: String): CurriculumNode = nodes.first { it.id == id }
    fun mastered(state: LearnerState, id: String): Boolean = state.progress[id]?.masteredAt != null
    fun available(state: LearnerState, node: CurriculumNode): Boolean = node.implemented && node.prerequisites.all { mastered(state, it) }
    fun next(state: LearnerState): CurriculumNode? = nodes.firstOrNull { available(state, it) && !mastered(state, it.id) }
    fun status(state: LearnerState, node: CurriculumNode): String = when {
        !node.implemented -> "规划中"
        state.progress[node.id]?.needsReview == true -> "需复习"
        mastered(state, node.id) -> if (node.id == "g00") "已认识" else "已初步掌握"
        !available(state, node) -> "需先学习前置内容"
        state.currentNode == node.id && state.sessionId != null -> "正在学习"
        else -> "可学习"
    }
}
