package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.KnowledgeItem

/** Creates physical-note questions and all six directions of the shared mapping. */
class QuestionFactory {
    fun create(type: QuestionType, position: FretPosition): Question {
        require(GuitarCore.isNaturalNote(position.note)) {
            "Questions only support natural notes"
        }
        val mapping = requireNotNull(GuitarCore.mappingForNote(position.note)) {
            "Natural notes must have a fixed mapping"
        }

        return when (type) {
            QuestionType.FretToNote -> Question(
                type = type,
                prompt = position.string.toString() + "弦 " + position.fret + "品 → ?",
                fretPosition = position,
                choices = AnswerOptions.notes,
                correctAnswer = position.note,
                knowledgeItemId = fretItemId(type, position),
                note = mapping.note,
                solfege = mapping.solfege,
                degree = mapping.degree,
            )
            QuestionType.FretToSolfege -> Question(
                type = type,
                prompt = position.string.toString() + "弦 " + position.fret + "品 → 固定唱名",
                fretPosition = position,
                choices = AnswerOptions.solfege,
                correctAnswer = mapping.solfege,
                knowledgeItemId = fretItemId(type, position),
                note = mapping.note,
                solfege = mapping.solfege,
                degree = mapping.degree,
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
        require(
            type == QuestionType.NoteToSolfege ||
                type == QuestionType.SolfegeToNote ||
                type == QuestionType.NoteToDegree ||
                type == QuestionType.SolfegeToDegree,
        ) {
            "This question type requires a note as its canonical item"
        }
        val mapping = requireNotNull(GuitarCore.mappingForNote(note)) {
            "Only the seven natural notes have fixed mappings"
        }
        return createMapping(type, mapping)
    }

    fun createForDegree(type: QuestionType, degree: Int): Question {
        require(type == QuestionType.DegreeToNote || type == QuestionType.DegreeToSolfege) {
            "This question type requires a scale degree as its canonical item"
        }
        val mapping = requireNotNull(GuitarCore.mappingForDegree(degree)) {
            "Scale degree must be between 1 and 7"
        }
        return createMapping(type, mapping)
    }

    fun knowledgeItemFor(question: Question): KnowledgeItem =
        KnowledgeItem(
            id = question.knowledgeItemId,
            questionType = question.type,
            string = question.fretPosition?.string,
            fret = question.fretPosition?.fret,
            note = question.note,
            solfege = question.solfege,
            degree = question.degree,
        )

    private fun createMapping(
        type: QuestionType,
        mapping: com.a3322505a.guitarlearning.core.NoteMapping,
    ): Question {
        val answer = when (type) {
            QuestionType.NoteToSolfege -> mapping.solfege
            QuestionType.SolfegeToNote,
            QuestionType.DegreeToNote -> mapping.note
            QuestionType.NoteToDegree,
            QuestionType.SolfegeToDegree -> mapping.degree.toString()
            QuestionType.DegreeToSolfege -> mapping.solfege
            else -> error("Unsupported mapping question type")
        }
        val prompt = when (type) {
            QuestionType.NoteToSolfege,
            QuestionType.NoteToDegree -> mapping.note + " = ?"
            QuestionType.SolfegeToNote,
            QuestionType.SolfegeToDegree -> mapping.solfege + " = ?"
            QuestionType.DegreeToNote,
            QuestionType.DegreeToSolfege -> mapping.degree.toString() + " = ?"
            else -> error("Unsupported mapping question type")
        }
        val choices = when (type) {
            QuestionType.NoteToSolfege,
            QuestionType.DegreeToSolfege -> AnswerOptions.solfege
            QuestionType.SolfegeToNote,
            QuestionType.DegreeToNote -> AnswerOptions.notes
            QuestionType.NoteToDegree,
            QuestionType.SolfegeToDegree -> AnswerOptions.degrees
            else -> error("Unsupported mapping question type")
        }
        return Question(
            type = type,
            prompt = prompt,
            fretPosition = null,
            choices = choices,
            correctAnswer = answer,
            knowledgeItemId = mappingItemId(type, mapping),
            note = mapping.note,
            solfege = mapping.solfege,
            degree = mapping.degree,
        )
    }

    private fun fretItemId(type: QuestionType, position: FretPosition): String =
        type.name + ":s" + position.string + ":f" + position.fret

    private fun mappingItemId(
        type: QuestionType,
        mapping: com.a3322505a.guitarlearning.core.NoteMapping,
    ): String = when (type) {
        QuestionType.DegreeToNote,
        QuestionType.DegreeToSolfege -> type.name + ":degree:" + mapping.degree
        else -> type.name + ":note:" + mapping.note
    }
}
