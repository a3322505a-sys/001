package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Settings

interface TrainingModule {
    val id: String
    val title: String

    fun buildQuestionBank(settings: Settings): List<TrainingQuestion>
}

/** Adapter that keeps all existing note and mapping behavior behind the common module boundary. */
class LegacyTrainingModule(
    private val enabledQuestionTypes: List<QuestionType>,
) : TrainingModule {
    override val id: String = "legacy"
    override val title: String = "现有训练"
    private val factory = QuestionFactory()

    init {
        require(enabledQuestionTypes.isNotEmpty()) { "At least one question type must be enabled" }
    }

    override fun buildQuestionBank(settings: Settings): List<TrainingQuestion> {
        val positions = GuitarCore.allPositions(
            strings = settings.selectedStrings.sorted(),
            frets = settings.fretStart..settings.fretEnd,
            naturalOnly = true,
        )
        val mappingNotes = GuitarCore.fixedMappings.map { it.note }
        val mappingDegrees = GuitarCore.fixedMappings.map { it.degree }
        return enabledQuestionTypes.distinct().flatMap { type ->
            when (type) {
                QuestionType.FretToNote, QuestionType.FretToSolfege ->
                    positions.map {
                        factory.create(
                            type = type,
                            position = it,
                            disambiguateOctave = settings.fretStart == 0 && settings.fretEnd == 12,
                        )
                    }
                QuestionType.NoteToSolfege,
                QuestionType.SolfegeToNote,
                QuestionType.NoteToDegree,
                QuestionType.SolfegeToDegree ->
                    mappingNotes.map { factory.createForNote(type, it) }
                QuestionType.DegreeToNote, QuestionType.DegreeToSolfege ->
                    mappingDegrees.map { factory.createForDegree(type, it) }
            }
        }.also { require(it.isNotEmpty()) { "Question bank must contain a question" } }
    }
}
