package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.math.abs

/** Concrete examples for lesson copy; never changes answer rules or learning evidence. */
object LessonExplanations {
    fun pitch(midi: Int): String = "${MusicFacts.noteNames[midi % 12]}${midi / 12 - 1}"
    fun fret(fret: Int): String = if (fret == 0) "空弦" else "${fret}品"
    fun location(c: Coordinate): String = "${MusicFacts.label(c.string, c.fret)}（第${c.string}弦${fret(c.fret)}）"
    fun distance(semitones: Int): String = when (semitones) {
        0 -> "音高相同"
        1 -> "差一个半音"
        2 -> "差一个全音"
        12 -> "差12个半音，即一个八度"
        else -> "差${semitones}个半音"
    }

    fun sameString(positions: List<Coordinate>): String {
        require(positions.isNotEmpty() && positions.map { it.string }.distinct().size == 1)
        val route = positions.joinToString(" → ") { "${MusicFacts.note(it.string, it.fret)}（${fret(it.fret)}）" }
        val steps = positions.zipWithNext().joinToString("\n") { (a, b) ->
            val gap = abs(b.fret - a.fret)
            val count = when (gap) { 1 -> "一品"; 2 -> "两品"; else -> "${gap}品" }
            "${MusicFacts.note(a.string, a.fret)}→${MusicFacts.note(b.string, b.fret)}：$count，${distance(gap)}。"
        }
        return "第${positions.first().string}弦：$route" + if (steps.isEmpty()) "。" else "\n$steps"
    }

    fun position(nodeId: String, target: Coordinate): String {
        if (target.fret == 12) {
            val open = Coordinate(target.string, 0)
            return "第${target.string}弦：${MusicFacts.label(open.string, 0)}（空弦）→ ${MusicFacts.label(target.string, 12)}（12品）\n" +
                "空弦→12品：12品，升高一个八度；音名相同，音高不同。"
        }
        // Refer only to this lesson and earlier lessons; do not introduce a later lesson's note names.
        val nodeIndex = Curriculum.nodes.indexOfFirst { it.id == nodeId }
        val candidates = (Curriculum.nodes.take(nodeIndex + 1).flatMap { it.positions } + target)
            .filter { it.string == target.string }.distinct().sortedBy { it.fret }
        val index = candidates.indexOf(target)
        val start = (index - 1).coerceAtLeast(0).coerceAtMost((candidates.size - 3).coerceAtLeast(0))
        val route = candidates.drop(start).take(3)
        return sameString(route) + "\n本题：${location(target)}。" +
            if (target == Coordinate(3, 4)) "与第2弦空弦B3同音高。" else ""
    }

    fun tab(c: Coordinate): String =
        "TAB：从上往下第${c.string}条线 → 第${c.string}弦；数字${c.fret} → ${fret(c.fret)}。\n" +
            "合起来：${location(c)}。${if (c.fret == 0) "空弦不用按品。" else "按指定弦、品定位。"}"
}
