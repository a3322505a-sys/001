package com.a3322505a.guitarlearning.learning

import kotlin.math.abs

/** Pitch relations use actual MIDI values; interval spelling remains explicit in the lesson. */
object MusicRelations {
    val major = listOf(0, 2, 4, 5, 7, 9, 11, 12)
    val naturalMinor = listOf(0, 2, 3, 5, 7, 8, 10, 12)
    val triads = linkedMapOf("大三和弦" to listOf(0, 4, 7), "小三和弦" to listOf(0, 3, 7),
        "减三和弦" to listOf(0, 3, 6), "增三和弦" to listOf(0, 4, 8))
    val intervals = listOf("同度", "小二度", "大二度", "小三度", "大三度", "纯四度", "三全音", "纯五度", "小六度", "大六度", "小七度", "大七度", "纯八度")
    fun semitones(first: Int, second: Int): Int = abs(second - first)
    fun pitchRelation(first: Int, second: Int): String = when {
        first == second -> "同音高"
        abs(first - second) == 12 -> "相差一个八度"
        Math.floorMod(first - second, 12) == 0 -> "同音名，跨多个八度"
        else -> "不同音名"
    }
    fun pitches(root: Int, offsets: List<Int>): List<Int> = offsets.map { root + it }
}
