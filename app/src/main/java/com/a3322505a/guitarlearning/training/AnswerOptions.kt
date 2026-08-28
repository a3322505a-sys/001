package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore

/** Answer domains derived from the same canonical mapping as the question factory. */
enum class AnswerKind {
    NOTE,
    SOLFEGE,
    DEGREE,
}

object AnswerOptions {
    val notes: List<String> = GuitarCore.fixedMappings.map { it.note }
    val solfege: List<String> = GuitarCore.fixedMappings.map { it.solfege }
    val degrees: List<String> = GuitarCore.fixedMappings.map { it.degree.toString() }

    fun forKind(kind: AnswerKind): List<String> = when (kind) {
        AnswerKind.NOTE -> notes
        AnswerKind.SOLFEGE -> solfege
        AnswerKind.DEGREE -> degrees
    }
}
