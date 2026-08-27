package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.KnowledgeItem

/** Creates the four question directions without any UI knowledge. */
class QuestionFactory {
    fun create(type: QuestionType, position: FretPosition): Question {
        require(GuitarCore.isNaturalNote(position.note)) {
            "V0.1 questions only support natural notes"
        }
        val solfege = requireNotNull(position.solfege) {
            "Natural notes must have fixed solfege"
        }
        return when (type) {
            QuestionType.FretToNote -> Question(
                type = type,
                prompt = "${position.string}弦 ${position.fret}品 → ?",
                fretPosition = position,
                choices = AnswerOptions.notes,
                correctAnswer = position.note,
                knowledgeItemId = fretItemId(type, position),
                note = position.note,
                solfege = solfege,
            )
            QuestionType.FretToSolfege -> Question(
                type = type,
                prompt = "${position.string}弦 ${position.fret}品 → 固定唱名",
                fretPosition = position,
                choices = AnswerOptions.solfege,
                correctAnswer = solfege,
                knowledgeItemId = fretItemId(type, position),
                note = position.note,
                solfege = solfege,
            )
            QuestionType.NoteToSolfege -> createForNote(type, position.note, solfege)
            QuestionType.SolfegeToNote -> createForNote(type, position.note, solfege)
        }
    }

    fun createForNote(type: QuestionType, note: String): Question {
        require(type == QuestionType.NoteToSolfege || type == QuestionType.SolfegeToNote) {
            "Note questions require a note-to-note-label direction"
        }
        require(GuitarCore.isNaturalNote(note)) {
            "V0.1 questions only support natural notes"
        }
        return createForNote(type, note, requireNotNull(GuitarCore.solfegeFor(note)))
    }

    fun knowledgeItemFor(question: Question): KnowledgeItem {
        val position = question.fretPosition
        return KnowledgeItem(
            id = question.knowledgeItemId,
            questionType = question.type,
            string = position?.string,
            fret = position?.fret,
            note = position?.note ?: when (question.type) {
                QuestionType.NoteToSolfege,
                QuestionType.SolfegeToNote -> question.correctAnswer.takeIf {
                    GuitarCore.isNaturalNote(it)
                } ?: requireNotNull(
                    GuitarCore.fixedSolfege.entries.firstOrNull { it.value == question.correctAnswer }?.key,
                )
                else -> error("Fret question must have a position")
            },
            solfege = position?.solfege ?: when (question.type) {
                QuestionType.NoteToSolfege -> question.correctAnswer
                QuestionType.SolfegeToNote -> requireNotNull(GuitarCore.solfegeFor(
                    question.correctAnswer,
                ))
                else -> error("Fret question must have a position")
            },
        )
    }

    private fun createForNote(type: QuestionType, note: String, solfege: String): Question {
        val answer = when (type) {
            QuestionType.NoteToSolfege -> solfege
            QuestionType.SolfegeToNote -> note
            else -> error("Unsupported note question type")
        }
        val prompt = when (type) {
            QuestionType.NoteToSolfege -> "$note = ?"
            QuestionType.SolfegeToNote -> "$solfege = ?"
            else -> error("Unsupported note question type")
        }
        return Question(
            type = type,
            prompt = prompt,
            fretPosition = null,
            choices = if (type == QuestionType.NoteToSolfege) {
                AnswerOptions.solfege
            } else {
                AnswerOptions.notes
            },
            correctAnswer = answer,
            knowledgeItemId = noteItemId(type, note),
            note = note,
            solfege = solfege,
        )
    }

    private fun fretItemId(type: QuestionType, position: FretPosition): String =
        "${type.name}:s${position.string}:f${position.fret}"

    private fun noteItemId(type: QuestionType, note: String): String =
        "${type.name}:note:$note"
}
