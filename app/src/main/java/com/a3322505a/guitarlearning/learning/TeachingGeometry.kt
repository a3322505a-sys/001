package com.a3322505a.guitarlearning.learning

import kotlin.math.pow

/** The viewport, hit targets, drawing and semantics all use these boundaries. */
class TeachingGeometry(val first: Int, val last: Int) {
    init { require(first in 0..15 && last in first..15) }
    private val weights = (first..last).map { if (it == 0) 0.65 else 2.0.pow(-(it - 1) / 12.0) }
    private val total = weights.sum()
    fun left(fret: Int): Float { require(fret in first..last); return (weights.take(fret - first).sum() / total).toFloat() }
    fun right(fret: Int): Float { require(fret in first..last); return (weights.take(fret - first + 1).sum() / total).toFloat() }
    fun center(fret: Int): Float = (left(fret) + right(fret)) / 2
    fun at(x: Float, y: Float): Coordinate? {
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return null
        val fret = (first..last).firstOrNull { x < right(it) } ?: last
        return Coordinate((y * 6).toInt().coerceIn(0, 5) + 1, fret)
    }
    fun inlays(fret: Int): Int = when (fret) { 3, 5, 7, 9, 15 -> 1; 12 -> 2; else -> 0 }
}
