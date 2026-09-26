package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts

/** The five C-major shapes in the user's reference, in adjacent-fret order.
 * The names are aliases of one shape; music facts always come from standard tuning. */
data class MajorScalePattern(val id: String, val solfegeName: String, val cagedName: String,
    val fretsByString: List<List<Int>>) {
    val title: String get() = "${solfegeName}指型 · CAGED ${cagedName}形"
    val positions: List<Coordinate> = fretsByString.flatMapIndexed { index, frets -> frets.map { Coordinate(index + 1, it) } }
    val firstFret: Int get() = positions.minOf { it.fret }
    val lastFret: Int get() = positions.maxOf { it.fret }
    val roots: List<Coordinate> get() = positions.filter { MusicFacts.midi(it.string, it.fret) % 12 == 0 }
    val degrees: Map<Coordinate, Int> get() = positions.associateWith { checkNotNull(MusicFacts.majorDegree(MusicFacts.midi(it.string, it.fret), 0)) }

    init {
        require(fretsByString.size == 6 && fretsByString.all { it == it.distinct().sorted() && it.isNotEmpty() })
        require(positions.all { MusicFacts.majorDegree(MusicFacts.midi(it.string, it.fret), 0) != null })
        require(roots.isNotEmpty())
    }
}

object MajorScalePatterns {
    // Strings 1 -> 6; the edge notes and overlaps follow reference 1000013046.jpg.
    val all = listOf(
        MajorScalePattern("mi", "mi", "C", listOf(
            listOf(0, 1, 3), listOf(0, 1, 3), listOf(0, 2),
            listOf(0, 2, 3), listOf(0, 2, 3), listOf(0, 1, 3))),
        MajorScalePattern("sol", "sol", "A", listOf(
            listOf(3, 5), listOf(3, 5, 6), listOf(2, 4, 5),
            listOf(2, 3, 5), listOf(2, 3, 5), listOf(3, 5))),
        MajorScalePattern("la", "la", "G", listOf(
            listOf(5, 7, 8), listOf(5, 6, 8), listOf(4, 5, 7),
            listOf(5, 7), listOf(5, 7, 8), listOf(5, 7, 8))),
        MajorScalePattern("si", "si", "E", listOf(
            listOf(7, 8, 10), listOf(8, 10), listOf(7, 9, 10),
            listOf(7, 9, 10), listOf(7, 8, 10), listOf(7, 8, 10))),
        MajorScalePattern("re", "re", "D", listOf(
            listOf(10, 12, 13), listOf(10, 12, 13), listOf(9, 10, 12),
            listOf(9, 10, 12), listOf(10, 12), listOf(10, 12, 13))),
    )
    val cMajorTriad: List<Coordinate> by lazy {
        listOf(60, 64, 67).map { midi ->
            all.first().positions.first { MusicFacts.midi(it.string, it.fret) == midi }
        }
    }
    fun forNode(id: String): MajorScalePattern = all.first { "pattern-${it.id}" == id }
    fun nodeId(pattern: MajorScalePattern) = "pattern-${pattern.id}"
}
