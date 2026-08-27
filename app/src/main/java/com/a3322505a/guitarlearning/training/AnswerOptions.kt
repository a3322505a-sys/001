package com.a3322505a.guitarlearning.training

/** Fixed V0.1 answer domains shared by the engine and the Compose component. */
enum class AnswerKind {
    NOTE,
    SOLFEGE,
}

object AnswerOptions {
    val notes: List<String> = listOf("C", "D", "E", "F", "G", "A", "B")
    val solfege: List<String> = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

    fun forKind(kind: AnswerKind): List<String> = when (kind) {
        AnswerKind.NOTE -> notes
        AnswerKind.SOLFEGE -> solfege
    }
}
