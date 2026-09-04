package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.core.NoteMapping
import com.a3322505a.guitarlearning.storage.KnowledgeItem

/** Creates physical-note questions and all six directions of the shared mapping. */
class QuestionFactory {
    fun create(
        type: QuestionType,
        position: FretPosition,
        disambiguateOctave: Boolean = false,
    ): Question {
        require(GuitarCore.isNaturalNote(position.note)) { "Questions only support natural notes" }
        val mapping = requireNotNull(GuitarCore.mappingForNote(position.note)) {
            "Natural notes must have a fixed mapping"
        }
        return when (type) {
            QuestionType.FretToNote -> fretboardQuestion(
                type = type,
                prompt = positionPrompt(position, disambiguateOctave),
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
            note = question.note,
            solfege = question.solfege.takeIf { it.isNotEmpty() },
            degree = question.degree.takeIf { it != 0 },
        )

    fun createCurriculumQuestion(
        level: Int,
        kind: String,
        prompt: String,
        anchor: FretPosition?,
        target: FretPosition,
        relationId: String,
    ): Question {
        val answerId = "s${target.string}:f${target.fret}"
        return TrainingQuestion(
            moduleId = TrainingModuleIds.FRET_NOTE,
            kind = kind,
            prompt = prompt,
            answerChoices = emptyList(),
            correctChoiceId = answerId,
            knowledgeItemId = "fretboard:lv$level:$relationId:$answerId",
            payload = FretboardCurriculumPayload(level, anchor, target, relationId),
            weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            answerMode = AnswerMode.FRETBOARD,
            correctAnswerValue = AnswerValue.FretPosition(target.string, target.fret),
            curriculumLevel = level,
        )
    }

    fun createFretboardNoteSetQuestion(
        note: String,
        targets: List<FretPosition>,
        rangeId: String,
        correctUniverse: List<FretPosition> = targets,
    ): Question {
        require(targets.isNotEmpty()) { "A fretboard note set needs at least one target" }
        require(targets.all { it.note == note }) { "Every fret target must match the prompt note" }
        require(correctUniverse.all { it.note == note }) {
            "Every correct-universe position must match the prompt note"
        }
        require(correctUniverse.containsAll(targets)) {
            "The correct universe must contain every required target"
        }
        val sortedTargets = targets.sortedWith(compareBy({ it.string }, { it.fret }))
        val sortedUniverse = correctUniverse.sortedWith(compareBy({ it.string }, { it.fret }))
        val signature = sortedTargets.joinToString("-") { "s${it.string}f${it.fret}" }
        val answer = AnswerValue.FretSet(
            sortedTargets.map { AnswerValue.FretPosition(it.string, it.fret) }.toSet(),
        )
        return TrainingQuestion(
            moduleId = TrainingModuleIds.FRET_NOTE,
            kind = "fret_to_note_set",
            prompt = note,
            answerChoices = emptyList(),
            correctChoiceId = signature,
            knowledgeItemId = "FretToNoteSet:$rangeId:$note:$signature",
            payload = FretboardNoteSetPayload(
                note = note,
                targets = sortedTargets,
                rangeId = rangeId,
                correctUniverse = sortedUniverse,
            ),
            weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            answerMode = AnswerMode.FRETBOARD_SET,
            correctAnswerValue = answer,
            curriculumLevel = 1,
        )
    }

    fun createSequenceCurriculumQuestion(
        level: Int,
        kind: String,
        prompt: String,
        targets: List<FretPosition>,
        relationId: String,
    ): Question {
        require(targets.size >= 2) { "A sequence question needs at least two targets" }
        val targetId = targets.joinToString(":") { "s${it.string}f${it.fret}" }
        val answer = AnswerValue.FretSequence(
            targets.map { AnswerValue.FretPosition(it.string, it.fret) },
        )
        return TrainingQuestion(
            moduleId = TrainingModuleIds.FRET_NOTE,
            kind = kind,
            prompt = prompt,
            answerChoices = emptyList(),
            correctChoiceId = targetId,
            knowledgeItemId = "fretboard:lv$level:$relationId:$targetId",
            payload = FretboardSequencePayload(level, targets, relationId),
            weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            answerMode = AnswerMode.FRETBOARD_SEQUENCE,
            correctAnswerValue = answer,
            curriculumLevel = level,
        )
    }

    fun createFretboardShapeQuestion(
        chordId: String,
        chordLabel: String,
        rootNote: String,
        order: Int,
        targets: List<FretPosition>,
    ): Question {
        require(order in 1..5) { "Chord shape order must be between 1 and 5" }
        require(targets.isNotEmpty()) { "A chord shape needs at least one target" }
        require(targets.all { it.fret in 0..4 }) { "Chord shapes must stay in first position" }
        val sortedTargets = targets.sortedWith(compareBy({ it.string }, { it.fret }))
        val targetId = sortedTargets.joinToString(":") { "s${it.string}f${it.fret}" }
        return TrainingQuestion(
            moduleId = TrainingModuleIds.FRET_NOTE,
            kind = "${chordId}_chord_shape",
            prompt = "Lv.6 · $chordLabel · 全选固定指法",
            answerChoices = emptyList(),
            correctChoiceId = targetId,
            knowledgeItemId = "fretboard:lv6:chord_shape:$chordId:$targetId",
            payload = FretboardShapePayload(chordId, rootNote, order, sortedTargets),
            weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            answerMode = AnswerMode.FRETBOARD_SET,
            correctAnswerValue = AnswerValue.FretSet(
                sortedTargets.map { AnswerValue.FretPosition(it.string, it.fret) }.toSet(),
            ),
            curriculumLevel = 6,
        )
    }

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

    private fun fretboardQuestion(
        type: QuestionType,
        prompt: String,
        knowledgeItemId: String,
        payload: FretNotePayload,
        weightPolicy: QuestionWeightPolicy,
    ): Question {
        val position = payload.position
        val answerId = "s" + position.string + ":f" + position.fret
        return TrainingQuestion(
            moduleId = TrainingModuleIds.FRET_NOTE,
            kind = type.name,
            prompt = prompt,
            answerChoices = emptyList(),
            correctChoiceId = answerId,
            knowledgeItemId = knowledgeItemId,
            payload = payload,
            weightPolicy = weightPolicy,
            answerMode = AnswerMode.FRETBOARD,
            correctAnswerValue = AnswerValue.FretPosition(position.string, position.fret),
        )
    }

    private fun positionPrompt(position: FretPosition, disambiguateOctave: Boolean): String {
        val base = position.string.toString() + "弦 · " + position.note
        return if (disambiguateOctave && position.fret in setOf(0, 12)) {
            base + " · " + position.fret + "品区域"
        } else {
            base
        }
    }

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
