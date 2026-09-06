package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.serialization.Serializable

@Serializable
data class FingerSpan(val finger: Int, val fret: Int, val firstString: Int, val lastString: Int = firstString) {
    init { require(finger in 1..4 && fret in 1..15 && firstString in 1..6 && lastString in firstString..6) }
    fun covers(string: Int): Boolean = string in firstString..lastString
}

@Serializable
data class ChordShape(val id: String, val title: String, val root: Int, val frets: List<Int?>, val fingers: List<FingerSpan>) {
    init {
        require(root in 0..11 && frets.size == 6 && frets.all { it == null || it in 0..15 })
        require(frets.any { it != null })
        frets.forEachIndexed { index, fret ->
            if (fret != null) require(fret == (fingers.filter { it.covers(index + 1) }.maxOfOrNull { it.fret } ?: 0))
        }
    }
    fun fret(string: Int): Int? = frets[string - 1]
    fun sounding(): List<Coordinate> = (6 downTo 1).mapNotNull { s -> fret(s)?.let { Coordinate(s, it) } }
    fun pitches(): List<Int> = sounding().map { MusicFacts.midi(it.string, it.fret) }
    fun fingerAt(c: Coordinate): Int? = fingers.firstOrNull { it.covers(c.string) && it.fret == c.fret }?.finger
}

enum class FingeringMode(val id: String, val title: String) {
    COLORS("colors", "颜色框"), NUMBERS("numbers", "手指编号"), NOTES("notes", "音名");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: COLORS }
}

object ChordShapes {
    val am = ChordShape("am-open", "Am 开放和弦", 9, listOf(0, 1, 2, 2, 0, null),
        listOf(FingerSpan(1, 1, 2), FingerSpan(2, 2, 4), FingerSpan(3, 2, 3)))
    val g5Two = ChordShape("g5-two", "G5 两音", 7, listOf(null, null, null, null, 5, 3),
        listOf(FingerSpan(1, 3, 6), FingerSpan(3, 5, 5)))
    val g5Three = ChordShape("g5-three", "G5 三音", 7, listOf(null, null, null, 5, 5, 3),
        listOf(FingerSpan(1, 3, 6), FingerSpan(3, 5, 5), FingerSpan(4, 5, 4)))
    val fBarre = ChordShape("f-barre", "F 大横按", 5, listOf(1, 1, 2, 3, 3, 1),
        listOf(FingerSpan(1, 1, 1, 6), FingerSpan(2, 2, 3), FingerSpan(3, 3, 5), FingerSpan(4, 3, 4)))
    val all = listOf(am, g5Two, g5Three, fBarre)
    fun get(id: String): ChordShape = all.first { it.id == id }
}
