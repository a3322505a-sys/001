package com.a3322505a.guitarlearning.learning

object Curriculum {
    val nodes = listOf(
        CurriculumNode("g00", "认识吉他", Category.GUITAR, "由最细的1弦数到最粗的6弦；从弦枕数品格，再用3、5、7、9品单点和12品双点定位。"),
        CurriculumNode("n00", "认识音名 E / F", Category.SYMBOL, "E、F是音的名字；先认字母，再在第1弦找到E（空弦）与F（1品）。", listOf("g00")),
        CurriculumNode("p01", "1弦 E / F", Category.FRETBOARD, "第1弦：E（空弦）→ F（1品）；E→F：一品，差一个半音。", listOf("n00"), listOf(Coordinate(1, 0), Coordinate(1, 1))),
        CurriculumNode("tab01", "认识 TAB", Category.READING, "TAB最上方的线→第1弦，数字0→空弦，数字1→第1品；把线和数字合起来找位置。", listOf("p01")),
        CurriculumNode("p02", "加入 G / B", Category.FRETBOARD, "第1弦：E（空弦）→ F（1品）→ G（3品）；E→F半音，F→G全音。第2弦空弦是B3。", listOf("p01"), listOf(Coordinate(1, 3), Coordinate(2, 0))),
        CurriculumNode("p03", "2弦 C / D", Category.FRETBOARD, "第2弦：B（空弦）→ C（1品）→ D（3品）；B→C：一品、半音；C→D：两品、全音。", listOf("p02"), listOf(Coordinate(2, 1), Coordinate(2, 3))),
        CurriculumNode("p04", "3弦 G / A", Category.FRETBOARD, "第3弦：G（空弦）→ A（2品）；G→A：两品，差一个全音。", listOf("p03"), listOf(Coordinate(3, 0), Coordinate(3, 2))),
        CurriculumNode("p05", "跨弦 B / D", Category.FRETBOARD, "第3弦：G（空弦）→ A（2品）→ B（4品），每步两品、一个全音。3弦4品与2弦空弦都是B3；4弦空弦是D3。", listOf("p04"), listOf(Coordinate(3, 4), Coordinate(4, 0))),
        CurriculumNode("p06", "4弦 E / F", Category.FRETBOARD, "第4弦：D（空弦）→ E（2品）→ F（3品）；D→E：两品、全音；E→F：一品、半音。", listOf("p05"), listOf(Coordinate(4, 2), Coordinate(4, 3))),
        CurriculumNode("p07", "5弦 A / B", Category.FRETBOARD, "第5弦：A（空弦）→ B（2品）；A→B：两品，差一个全音。", listOf("p06"), listOf(Coordinate(5, 0), Coordinate(5, 2))),
        CurriculumNode("p08", "加入 C / E", Category.FRETBOARD, "第5弦：A（空弦）→ B（2品）→ C（3品）；A→B全音，B→C半音。第6弦空弦是E2。", listOf("p07"), listOf(Coordinate(5, 3), Coordinate(6, 0))),
        CurriculumNode("p09", "6弦 F / G", Category.FRETBOARD, "第6弦：E（空弦）→ F（1品）→ G（3品）；E→F：一品、半音；F→G：两品、全音。", listOf("p08"), listOf(Coordinate(6, 1), Coordinate(6, 3))),
        // V2 mapping keeps its P03 prerequisite and its own directional/key evidence.
        CurriculumNode("mapping", "唱名与级数", Category.SYMBOL, "音名C↔固定唱名Do；在C大调中，C（1级）→ D（2级）→ E（3级）。固定唱名和调内级数分别练习。", listOf("p03")),
        CurriculumNode("tab02", "TAB 短句", Category.READING, "同弦短句→相邻弦短句；已教弦品、重复音及陌生组合，短句不预设节奏。", listOf("tab01", "p03")),
        CurriculumNode("staff", "五线谱入门", Category.READING, "例如谱面E5→实际E4→1弦空弦；吉他实际发声比谱面低一个八度，同音高位置都接受。", listOf("tab01", "p03")),
        CurriculumNode("staff02", "五线谱短句", Category.READING, "从左到右逐音读谱，例如谱面E5→F5，实际弹E4→F4；每个音降低八度后定位。", listOf("staff")),
        CurriculumNode("middle", "5品 A / E", Category.FRETBOARD, "第1弦：G（3品）→ A（5品）；第2弦：D（3品）→ E（5品）。两组都相隔两品、一个全音。", listOf("p09"), listOf(Coordinate(1, 5), Coordinate(2, 5))),
        CurriculumNode("m02", "高音 B / C", Category.FRETBOARD, "第1弦：A（5品）→ B（7品）→ C（8品）；A→B全音，B→C半音。", listOf("middle"), listOf(Coordinate(1, 7), Coordinate(1, 8))),
        CurriculumNode("m03", "2弦 F / G", Category.FRETBOARD, "第2弦：E（5品）→ F（6品）→ G（8品）；E→F半音，F→G全音。", listOf("m02"), listOf(Coordinate(2, 6), Coordinate(2, 8))),
        CurriculumNode("m04", "3弦 C / D", Category.FRETBOARD, "第3弦：B（4品）→ C（5品）→ D（7品）；B→C半音，C→D全音。", listOf("m03"), listOf(Coordinate(3, 5), Coordinate(3, 7))),
        CurriculumNode("m05", "4弦 G / A", Category.FRETBOARD, "第4弦：F（3品）→ G（5品）→ A（7品）；每步两品、一个全音。", listOf("m04"), listOf(Coordinate(4, 5), Coordinate(4, 7))),
        CurriculumNode("m06", "5弦 D / E", Category.FRETBOARD, "第5弦：C（3品）→ D（5品）→ E（7品）；每步两品、一个全音。", listOf("m05"), listOf(Coordinate(5, 5), Coordinate(5, 7))),
        CurriculumNode("m07", "跨弦 F / A", Category.FRETBOARD, "第5弦：E（7品）→ F（8品），一品、半音；第6弦：G（3品）→ A（5品），两品、全音。", listOf("m06"), listOf(Coordinate(5, 8), Coordinate(6, 5))),
        CurriculumNode("m08", "6弦 B / C", Category.FRETBOARD, "第6弦：A（5品）→ B（7品）→ C（8品）；A→B全音，B→C半音。", listOf("m07"), listOf(Coordinate(6, 7), Coordinate(6, 8))),
        CurriculumNode("full", "12品 E / B", Category.FRETBOARD, "第1弦：E4（空弦）→ E5（12品）；第2弦：B3（空弦）→ B4（12品）。同弦升12品，高一个八度。", listOf("m08"), listOf(Coordinate(1, 12), Coordinate(2, 12))),
        CurriculumNode("h02", "12品 G / D", Category.FRETBOARD, "第3弦：G3（空弦）→ G4（12品）；第4弦：D3（空弦）→ D4（12品）。同音名，音高升一个八度。", listOf("full"), listOf(Coordinate(3, 12), Coordinate(4, 12))),
        CurriculumNode("h03", "12品 A / E", Category.FRETBOARD, "第5弦：A2（空弦）→ A3（12品）；第6弦：E2（空弦）→ E3（12品）。同音名，音高升一个八度。", listOf("h02"), listOf(Coordinate(5, 12), Coordinate(6, 12))),
        CurriculumNode("h04", "10品 D / A", Category.FRETBOARD, "第1弦：C（8品）→ D（10品）→ E（12品）；第2弦：G（8品）→ A（10品）→ B（12品）。每步两品、一个全音。", listOf("h03"), listOf(Coordinate(1, 10), Coordinate(2, 10))),
        CurriculumNode("h05", "3弦 E / F", Category.FRETBOARD, "第3弦：D（7品）→ E（9品）→ F（10品）；D→E全音，E→F半音。", listOf("h04"), listOf(Coordinate(3, 9), Coordinate(3, 10))),
        CurriculumNode("h06", "4弦 B / C", Category.FRETBOARD, "第4弦：A（7品）→ B（9品）→ C（10品）；A→B全音，B→C半音。", listOf("h05"), listOf(Coordinate(4, 9), Coordinate(4, 10))),
        CurriculumNode("h07", "10品 G / D", Category.FRETBOARD, "第5弦：F（8品）→ G（10品）→ A（12品）；第6弦：C（8品）→ D（10品）→ E（12品）。每步两品、一个全音。", listOf("h06"), listOf(Coordinate(5, 10), Coordinate(6, 10))),
        CurriculumNode("chord-am", "Am 开放和弦形态", Category.ADVANCED, "Am：A–C–E；5弦空弦→4弦2品→3弦2品→2弦1品→1弦空弦，6弦不弹。练习手机逐弦定位。", listOf("p07")),
        CurriculumNode("chord-g5", "G5 两音与三音形态", Category.ADVANCED, "G2（6弦3品）→ D3（5弦5品）→ G3（4弦5品）；先根音加纯五度，再加高八度根音，没有三音。", listOf("p09")),
        CurriculumNode("chord-f", "F 横按形态", Category.ADVANCED, "食指横按1品；例如5弦另按3品时，实际发C3，不是1品的B♭2。按各弦最高按弦品位定位。", listOf("chord-am", "chord-g5")),
        CurriculumNode("structure", "半音与全音", Category.ADVANCED, "第1弦：E（空弦）→ F（1品）→ G（3品）；E→F一品、半音，F→G两品、全音。", listOf("p03")),
        CurriculumNode("pitch-relations", "同音名、同音高与八度", Category.ADVANCED, "B3（2弦空弦）= B3（3弦4品）；E4（1弦空弦）比E3（4弦2品）高一个八度。", listOf("p09", "structure")),
        CurriculumNode("intervals", "音程与高低方向", Category.ADVANCED, "例如C4→E4：上行4个半音，是大三度；E4→C4：下行4个半音，仍是大三度。", listOf("structure", "p03")),
        CurriculumNode("scale-major", "大调音阶结构", Category.ADVANCED, "C→D→E→F→G→A→B→C；相邻步距为全、全、半、全、全、全、半，再练上下行定位。", listOf("intervals", "mapping", "p09")),
        CurriculumNode("scale-minor", "自然小调音阶结构", Category.ADVANCED, "A→B→C→D→E→F→G→A；相邻步距为全、半、全、全、半、全、全，主音是A。", listOf("scale-major")),
        CurriculumNode("triads", "三和弦的根音、三音与五音", Category.ADVANCED, "例如C→E→G：先4个半音、再3个半音，是大三和弦；C→E♭→G先3后4，是小三和弦。", listOf("intervals", "mapping")),
        CurriculumNode("power-structure", "强力和弦结构", Category.ADVANCED, "G2→D3相隔7个半音，是纯五度；再加G3只多一个根音八度，没有加入三音。", listOf("chord-g5", "intervals")),
        CurriculumNode("cross-position", "跨把位的同音高", Category.ADVANCED, "E4（1弦空弦）= E4（2弦5品）= E4（3弦9品）；换位置，实际音高不变。", listOf("h07", "pitch-relations")),
        CurriculumNode("ear-intervals", "有参照的音程听辨", Category.ADVANCED, "先听参考音→再听第二音→比较方向与距离；例如上行7个半音是纯五度，不要求凭空报音名。", listOf("intervals")),
        CurriculumNode("ear-triads", "有根音的和弦听辨", Category.ADVANCED, "先听根音→再听和弦→比较根音、三音、五音；例如大三和弦是先4个半音、再3个半音。", listOf("triads")),
    ) + FurtherLessons.nodes
    fun positionSuccessor(id: String): CurriculumNode? = nodes.firstOrNull {
        it.category == Category.FRETBOARD && it.positions.isNotEmpty() && it.implemented && id in it.prerequisites
    }
    fun noteOptions(id: String): List<String> = nodes.take(nodes.indexOfFirst { it.id == id } + 1)
        .flatMap { it.positions }.map { com.a3322505a.guitarlearning.core.MusicFacts.note(it.string, it.fret) }.distinct()
    fun node(id: String): CurriculumNode = nodes.first { it.id == id }
    fun mastered(state: LearnerState, id: String): Boolean = state.progress[id]?.masteredAt != null
    fun available(state: LearnerState, node: CurriculumNode): Boolean = node.implemented && node.prerequisites.all { mastered(state, it) } && (node.id != "rework-key" || FurtherLessons.hasOwnWork(state))
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
