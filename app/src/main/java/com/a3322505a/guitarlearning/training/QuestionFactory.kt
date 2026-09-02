package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.core.NoteMapping
import com.a3322505a.guitarlearning.storage.KnowledgeItem

/** Creates physical-note questions and all six directions of the shared mapping. */
class QuestionFactory {
    fun create(type: QuestionType, position: FretPosition): Question {
        require(GuitarCore.isNaturalNote(position.note)) { "Questions only support natural notes" }
        val mapping = requireNotNull(GuitarCore.mappingForNote(position.note)) {
            "Natural notes must have a fixed mapping"
        }
        return when (type) {
            QuestionType.FretToNote -> legacyQuestion(
                type = type,
                prompt = position.string.toString() + "弦 " + position.fret + "品 → ?",
                choices = AnswerOptions.notes,
                correctAnswer = position.note,
                knowledgeItemId = fretItemId(type, position),
                payload = FretNotePayload(
                    type,
                    position,
                    mapping.note,
                    mapping.solfege,
                    mapping.degree,
                ),
                weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            )
            QuestionType.FretToSolfege -> legacyQuestion(
                type = type,
                prompt = position.string.toString() + "弦 " + position.fret + "品 → 固定唱名",
                choices = AnswerOptions.solfege,
                correctAnswer = mapping.solfege,
                knowledgeItemId = fretItemId(type, position),
                payload = FretNotePayload(
                    type,
                    position,
                    mapping.note,
                    mapping.solfege,
                    mapping.degree,
                ),
            )
            QuestionType.NoteToSolfege,
            QuestionType.SolfegeToNote,
            QuestionType.NoteToDegree,
            QuestionType.SolfegeToDegree -> createForNote(type, mapping.note)
            QuestionType.DegreeToNote,
            QuestionType.DegreeToSolfege -> createForDegree(type, mapping.degree)
        }
    }

    fun createForNote(type: QuestionType, note: String): Question {
        require(type in noteCanonicalTypes) { "This question type requires a note" }
        return createMapping(type, requireNotNull(GuitarCore.mappingForNote(note)))
    }

    fun createForDegree(type: QuestionType, degree: Int): Question {
        require(type in degreeCanonicalTypes) { "This question type requires a scale degree" }
        return createMapping(type, requireNotNull(GuitarCore.mappingForDegree(degree)))
    }

    fun knowledgeItemFor(question: Question): KnowledgeItem =
        KnowledgeItem(
            id = question.knowledgeItemId,
            moduleId = question.moduleId,
            kind = question.kind,
            questionType = question.type,
            string = question.fretPosition?.string,
            fret = question.fretPosition?.fret,
            note = question.note.takeIf { question.type != null },
            solfege = question.solfege.takeIf { question.type != null },
            degree = question.degree.takeIf { question.type != null },
        )

    private fun createMapping(type: QuestionType, mapping: NoteMapping): Question {
        val answer = when (type) {
            QuestionType.NoteToSolfege -> mapping.solfege
            QuestionType.SolfegeToNote, QuestionType.DegreeToNote -> mapping.note
            QuestionType.NoteToDegree, QuestionType.SolfegeToDegree -> mapping.degree.toString()
            QuestionType.DegreeToSolfege -> mapping.solfege
            else -> error("Unsupported mapping question type")
        }
        val prompt = when (type) {
            QuestionType.NoteToSolfege, QuestionType.NoteToDegree -> mapping.note + " = ?"
            QuestionType.SolfegeToNote, QuestionType.SolfegeToDegree -> mapping.solfege + " = ?"
            QuestionType.DegreeToNote, QuestionType.DegreeToSolfege ->
                mapping.degree.toString() + " = ?"
            else -> error("Unsupported mapping question type")
        }
        val choices = when (type) {
            QuestionType.NoteToSolfege, QuestionType.DegreeToSolfege -> AnswerOptions.solfege
            QuestionType.SolfegeToNote, QuestionType.DegreeToNote -> AnswerOptions.notes
            QuestionType.NoteToDegree, QuestionType.SolfegeToDegree -> AnswerOptions.degrees
            else -> error("Unsupported mapping question type")
        }
        return legacyQuestion(
            type = type,
            prompt = prompt,
            choices = choices,
            correctAnswer = answer,
            knowledgeItemId = mappingItemId(type, mapping),
            payload = MappingPayload(type, mapping.note, mapping.solfege, mapping.degree),
        )
    }

    private fun legacyQuestion(
        type: QuestionType,
        prompt: String,
        choices: List<String>,
        correctAnswer: String,
        knowledgeItemId: String,
        payload: QuestionPayload,
        weightPolicy: QuestionWeightPolicy = QuestionWeightPolicy.MASTERY,
    ): Question = TrainingQuestion(
        moduleId = if (type == QuestionType.FretToNote || type == QuestionType.FretToSolfege) {
            TrainingModuleIds.FRET_NOTE
        } else {
            TrainingModuleIds.NOTE_MAPPING
        },
        kind = type.name,
        prompt = prompt,
        answerChoices = choices.map { AnswerChoice(id = it, label = it) },
        correctChoiceId = correctAnswer,
        knowledgeItemId = knowledgeItemId,
        payload = payload,
        weightPolicy = weightPolicy,
    )

    private fun fretItemId(type: QuestionType, position: FretPosition): String =
        type.name + ":s" + position.string + ":f" + position.fret

    private fun mappingItemId(type: QuestionType, mapping: NoteMapping): String = when (type) {
        QuestionType.DegreeToNote, QuestionType.DegreeToSolfege ->
            type.name + ":degree:" + mapping.degree
        else -> type.name + ":note:" + mapping.note
    }

    private companion object {
        val noteCanonicalTypes = setOf(
            QuestionType.NoteToSolfege,
            QuestionType.SolfegeToNote,
            QuestionType.NoteToDegree,
            QuestionType.SolfegeToDegree,
        )
        val degreeCanonicalTypes = setOf(
            QuestionType.DegreeToNote,
            QuestionType.DegreeToSolfege,
        )
    }
}
