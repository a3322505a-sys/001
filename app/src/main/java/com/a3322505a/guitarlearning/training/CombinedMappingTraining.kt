package com.a3322505a.guitarlearning.training

// LEGACY v1 mapping material/state machine, not the planned v2 Curriculum "mapping" node.
// Reuse requires v2 tasks/evidence and explicit key context for degrees; see docs/legacy-v1.md.

import com.a3322505a.guitarlearning.audio.PitchCatalog
import com.a3322505a.guitarlearning.audio.PitchCue
import com.a3322505a.guitarlearning.audio.PitchPlaybackStyle
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.core.NoteMapping
import kotlin.random.Random

enum class CombinedStep {
    FIRST,
    SECOND,
}

enum class MappingForm(val level: Int, val label: String) {
    SINGLE(1, "单点"),
    PAIR(2, "双点"),
    SHORT_SEQUENCE(3, "短序列"),
    MISSING(4, "缺失项"),
    DEGREE_PATTERN(5, "级数模式"),
    CHORD_SET(6, "和弦音集合"),
    ;

    companion object {
        fun forLevel(level: Int): MappingForm = entries.single { it.level == level }
    }
}

data class CombinedQuestion(
    val id: Int,
    val mappings: List<NoteMapping>,
    val sourceType: AnswerKind,
    val firstTargetType: AnswerKind,
    val secondTargetType: AnswerKind,
    val form: MappingForm = MappingForm.SINGLE,
    val missingIndex: Int? = null,
    val structuralChoices: List<String> = emptyList(),
    val usesAudioPrompt: Boolean = false,
) {
    init {
        require(mappings.isNotEmpty()) { "A mapping question needs at least one value" }
        require(sourceType != firstTargetType) { "Source and target representations must differ" }
        require(form == MappingForm.SINGLE || structuralChoices.isNotEmpty()) {
            "A structural mapping question needs answer choices"
        }
        require(form == MappingForm.MISSING || missingIndex == null) {
            "Only a missing-item question may define a missing index"
        }
        if (form == MappingForm.MISSING) {
            requireNotNull(missingIndex)
            require(missingIndex in mappings.indices)
        }
    }

    val level: Int
        get() = form.level

    val note: String
        get() = mappings.first().note

    val solfege: String
        get() = mappings.first().solfege

    val degree: Int
        get() = mappings.first().degree

    fun valueFor(kind: AnswerKind): String = when (kind) {
        AnswerKind.NOTE -> note
        AnswerKind.SOLFEGE -> solfege
        AnswerKind.DEGREE -> degree.toString()
    }

    fun targetFor(step: CombinedStep): AnswerKind = when {
        form != MappingForm.SINGLE -> firstTargetType
        step == CombinedStep.FIRST -> firstTargetType
        else -> secondTargetType
    }

    fun correctAnswerFor(step: CombinedStep): String = when {
        form == MappingForm.SINGLE -> valueFor(targetFor(step))
        form == MappingForm.MISSING -> valueAt(requireNotNull(missingIndex), sourceType)
        else -> render(mappings, firstTargetType)
    }

    val semanticAnswer: AnswerValue
        get() {
            val values = when (form) {
                MappingForm.MISSING -> listOf(valueAt(requireNotNull(missingIndex), sourceType))
                else -> mappings.map { it.valueFor(firstTargetType) }
            }
            return when (form) {
                MappingForm.PAIR,
                MappingForm.SHORT_SEQUENCE,
                MappingForm.DEGREE_PATTERN -> AnswerValue.SymbolSequence(values)
                MappingForm.CHORD_SET -> AnswerValue.SymbolSet(values.toSet())
                MappingForm.SINGLE,
                MappingForm.MISSING -> AnswerValue.Choice(values.single())
            }
        }

    val audioCue: PitchCue?
        get() = if (usesAudioPrompt) {
            PitchCue(
                pitches = mappings.map { PitchCatalog.forNaturalNote(it.note) },
                style = if (form == MappingForm.CHORD_SET) {
                    PitchPlaybackStyle.CHORD
                } else {
                    PitchPlaybackStyle.SEQUENCE
                },
            )
        } else {
            null
        }

    fun promptFor(step: CombinedStep): String = when {
        usesAudioPrompt && form == MappingForm.SINGLE -> "♪ 听声音，选择对应符号"
        usesAudioPrompt -> "♪ 听声音结构，选择对应符号"
        else -> visualPromptFor(step)
    }

    private fun visualPromptFor(step: CombinedStep): String = when (form) {
        MappingForm.SINGLE -> when (step) {
            CombinedStep.FIRST -> valueFor(sourceType) + " = ?"
            CombinedStep.SECOND ->
                valueFor(sourceType) + " = " + valueFor(firstTargetType) + " = ?"
        }
        MappingForm.MISSING -> {
            val blanked = mappings.mapIndexed { index, mapping ->
                if (index == missingIndex) "?" else mapping.valueFor(sourceType)
            }.joinToString(" ")
            blanked + " = " + render(mappings, firstTargetType)
        }
        else -> render(mappings, sourceType) + " = ?"
    }

    private fun render(
        values: List<NoteMapping>,
        kind: AnswerKind,
    ): String {
        val separator = if (form == MappingForm.PAIR) " → " else " "
        return values.joinToString(separator) { it.valueFor(kind) }
    }

    private fun valueAt(index: Int, kind: AnswerKind): String = mappings[index].valueFor(kind)

    private fun NoteMapping.valueFor(kind: AnswerKind): String =
        when (kind) {
            AnswerKind.NOTE -> note
            AnswerKind.SOLFEGE -> solfege
            AnswerKind.DEGREE -> degree.toString()
        }
}

class CombinedQuestionFactory(
    private val random: Random = Random.Default,
) {
    private var nextId = 0

    fun create(level: Int = 1, audioPrompt: Boolean = false): CombinedQuestion {
        require(level in 1..6) { "Mapping level must be between 1 and 6" }
        return if (level == 1) {
            createSingle(audioPrompt)
        } else {
            createStructure(MappingForm.forLevel(level), audioPrompt)
        }
    }

    private fun createSingle(audioPrompt: Boolean): CombinedQuestion {
        val mapping = GuitarCore.fixedMappings[random.nextInt(GuitarCore.fixedMappings.size)]
        val path = AnswerKind.entries.shuffled(random)
        return CombinedQuestion(
            id = nextId++,
            mappings = listOf(mapping),
            sourceType = path[0],
            firstTargetType = path[1],
            secondTargetType = path[2],
            usesAudioPrompt = audioPrompt,
        )
    }

    private fun createStructure(form: MappingForm, audioPrompt: Boolean): CombinedQuestion {
        val path = AnswerKind.entries.shuffled(random)
        val degreePattern = patterns.getValue(form).random(random)
        val mappings = degreePattern.map { degree ->
            requireNotNull(GuitarCore.mappingForDegree(degree))
        }
        val missingIndex = if (form == MappingForm.MISSING) random.nextInt(mappings.size) else null
        val correct = if (missingIndex != null) {
            mappings[missingIndex].valueFor(path[0])
        } else {
            render(mappings, path[1], form)
        }
        val choices = if (missingIndex != null) {
            AnswerOptions.forKind(path[0]).shuffled(random)
        } else {
            structuralChoices(mappings, path[1], form, correct)
        }
        return CombinedQuestion(
            id = nextId++,
            mappings = mappings,
            sourceType = path[0],
            firstTargetType = path[1],
            secondTargetType = path[2],
            form = form,
            missingIndex = missingIndex,
            structuralChoices = choices,
            usesAudioPrompt = audioPrompt && form != MappingForm.MISSING,
        )
    }

    private fun structuralChoices(
        mappings: List<NoteMapping>,
        target: AnswerKind,
        form: MappingForm,
        correct: String,
    ): List<String> {
        val distractors = (1..6).map { shift ->
            val shifted = mappings.map { mapping ->
                val degree = ((mapping.degree - 1 + shift) % 7) + 1
                requireNotNull(GuitarCore.mappingForDegree(degree))
            }
            render(shifted, target, form)
        }
        return (listOf(correct) + distractors)
            .distinct()
            .take(4)
            .shuffled(random)
    }

    private fun render(
        mappings: List<NoteMapping>,
        kind: AnswerKind,
        form: MappingForm,
    ): String {
        val separator = if (form == MappingForm.PAIR) " → " else " "
        return mappings.joinToString(separator) { it.valueFor(kind) }
    }

    private fun NoteMapping.valueFor(kind: AnswerKind): String =
        when (kind) {
            AnswerKind.NOTE -> note
            AnswerKind.SOLFEGE -> solfege
            AnswerKind.DEGREE -> degree.toString()
        }

    fun shuffledAnswers(kind: AnswerKind): List<String> =
        AnswerOptions.forKind(kind).shuffled(random)

    private companion object {
        val patterns = mapOf(
            MappingForm.PAIR to listOf(
                listOf(1, 3),
                listOf(2, 5),
                listOf(4, 6),
                listOf(5, 1),
            ),
            MappingForm.SHORT_SEQUENCE to listOf(
                listOf(1, 2, 3),
                listOf(3, 2, 1),
                listOf(1, 3, 5),
                listOf(5, 4, 2),
            ),
            MappingForm.MISSING to listOf(
                listOf(1, 2, 3),
                listOf(1, 3, 5),
                listOf(5, 6, 5, 3),
            ),
            MappingForm.DEGREE_PATTERN to listOf(
                listOf(1, 2, 3, 5),
                listOf(5, 6, 5, 3),
                listOf(3, 2, 1),
                listOf(5, 4, 2),
            ),
            MappingForm.CHORD_SET to listOf(
                listOf(1, 3, 5),
                listOf(2, 4, 6),
                listOf(3, 5, 7),
            ),
        )
    }
}

sealed interface CombinedMappingState {
    val question: CombinedQuestion
    val step: CombinedStep
    val choices: List<String>
    val correctAnswer: String

    val prompt: String
        get() = question.promptFor(step)

    data class AwaitingAnswer(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
    ) : CombinedMappingState

    data class Correct(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
        val submittedAnswer: String,
        val completesQuestion: Boolean,
    ) : CombinedMappingState

    data class Incorrect(
        override val question: CombinedQuestion,
        override val step: CombinedStep,
        override val choices: List<String>,
        override val correctAnswer: String,
        val submittedAnswer: String,
    ) : CombinedMappingState
}

/**
 * Coordinates one complete three-way mapping question. Statistics are updated once per
 * complete question, never once per tap.
 */
class CombinedMappingStateMachine(
    random: Random = Random.Default,
    initialLevel: Int = 1,
    initialAudioPromptsEnabled: Boolean = false,
) {
    private val factory = CombinedQuestionFactory(random)
    var selectedLevel: Int = initialLevel
        private set

    var audioPromptsEnabled: Boolean = initialAudioPromptsEnabled
        private set

    private var current: CombinedMappingState = awaiting(
        factory.create(selectedLevel, audioPromptsEnabled),
        CombinedStep.FIRST,
    )

    var correctCount: Int = 0
        private set

    var errorCount: Int = 0
        private set

    val state: CombinedMappingState
        get() = current

    fun submitAnswer(answer: String): CombinedMappingState {
        val awaiting = current as? CombinedMappingState.AwaitingAnswer ?: return current
        if (answer !in awaiting.choices) return current

        current = if (answer == awaiting.correctAnswer) {
            val completesQuestion =
                awaiting.question.usesAudioPrompt ||
                    awaiting.question.form != MappingForm.SINGLE ||
                    awaiting.step == CombinedStep.SECOND
            if (completesQuestion) correctCount += 1
            CombinedMappingState.Correct(
                question = awaiting.question,
                step = awaiting.step,
                choices = awaiting.choices,
                correctAnswer = awaiting.correctAnswer,
                submittedAnswer = answer,
                completesQuestion = completesQuestion,
            )
        } else {
            errorCount += 1
            CombinedMappingState.Incorrect(
                question = awaiting.question,
                step = awaiting.step,
                choices = awaiting.choices,
                correctAnswer = awaiting.correctAnswer,
                submittedAnswer = answer,
            )
        }
        return current
    }

    fun advanceAfterCorrect(): CombinedMappingState {
        val correct = current as? CombinedMappingState.Correct ?: return current
        current = if (correct.completesQuestion) {
            awaiting(factory.create(selectedLevel, audioPromptsEnabled), CombinedStep.FIRST)
        } else {
            awaiting(correct.question, CombinedStep.SECOND)
        }
        return current
    }

    fun nextQuestion(): CombinedMappingState {
        check(current is CombinedMappingState.Incorrect) {
            "Only an incorrect question requires manual advancement"
        }
        current = awaiting(
            factory.create(selectedLevel, audioPromptsEnabled),
            CombinedStep.FIRST,
        )
        return current
    }

    fun selectLevel(level: Int): CombinedMappingState {
        require(level in 1..6) { "Mapping level must be between 1 and 6" }
        selectedLevel = level
        current = awaiting(factory.create(level, audioPromptsEnabled), CombinedStep.FIRST)
        return current
    }

    fun setAudioPromptsEnabled(enabled: Boolean): CombinedMappingState {
        audioPromptsEnabled = enabled
        current = awaiting(factory.create(selectedLevel, enabled), CombinedStep.FIRST)
        return current
    }

    private fun awaiting(
        question: CombinedQuestion,
        step: CombinedStep,
    ): CombinedMappingState.AwaitingAnswer {
        val target = question.targetFor(step)
        return CombinedMappingState.AwaitingAnswer(
            question = question,
            step = step,
            choices = if (question.form == MappingForm.SINGLE) {
                factory.shuffledAnswers(target)
            } else {
                question.structuralChoices
            },
            correctAnswer = question.correctAnswerFor(step),
        )
    }
}
