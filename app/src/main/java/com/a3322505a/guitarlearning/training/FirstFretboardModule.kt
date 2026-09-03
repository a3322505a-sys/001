package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.LevelProgress
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings

object FretboardLevelRules {
    const val MINIMUM_ATTEMPTS = 20
    const val REQUIRED_CORRECT = 18
    const val MAXIMUM_LEVEL = 6

    fun record(progress: LevelProgress, isCorrect: Boolean): LevelProgress =
        progress.copy(recentResults = (progress.recentResults + isCorrect).takeLast(20))

    fun qualifies(progress: LevelProgress): Boolean =
        progress.attempts == MINIMUM_ATTEMPTS && progress.correct >= REQUIRED_CORRECT
}

object FirstPositionGrowthRules {
    const val MINIMUM_STAGE_ATTEMPTS = 10
    const val REQUIRED_CORRECT_PER_ITEM = 2
    const val MAXIMUM_MASTERED_WEIGHT = 1.0

    fun qualifies(
        stageAttempts: Int,
        questions: List<Question>,
        progressById: Map<String, Progress>,
    ): Boolean = stageAttempts >= MINIMUM_STAGE_ATTEMPTS && questions.all { question ->
        val progress = progressById[question.knowledgeItemId] ?: return@all false
        progress.correct >= REQUIRED_CORRECT_PER_ITEM &&
            progress.weight <= MAXIMUM_MASTERED_WEIGHT
    }
}

/** Lv.1 note sets plus the existing Lv.2–Lv.6 fretboard curriculum. */
class FirstFretboardModule : TrainingModule {
    override val id: String = TrainingModuleIds.FRET_NOTE
    override val title: String = "第一指板"
    private val factory = QuestionFactory()

    override fun buildQuestionBank(settings: Settings): List<TrainingQuestion> {
        val effectiveFretEnd = if (
            settings.noteTrainingRangeId == NoteTrainingRange.LOW_POSITION.name &&
            !settings.firstPositionComplete
        ) {
            settings.firstPositionMaxFret
        } else {
            settings.fretEnd
        }
        val positions = GuitarCore.allPositions(
            strings = settings.selectedStrings.sorted(),
            frets = settings.fretStart..effectiveFretEnd,
            naturalOnly = true,
        )
        return buildList {
            addAll(levelOne(positions, settings))
            if (!isGrowingFirstPosition(settings)) {
                if (settings.unlockedFretboardLevel >= 2) addAll(levelTwo(positions))
                if (settings.unlockedFretboardLevel >= 3) addAll(levelThree(positions))
                if (settings.unlockedFretboardLevel >= 4) addAll(levelFour(positions))
                if (settings.unlockedFretboardLevel >= 5) addAll(levelFive(positions))
                if (settings.unlockedFretboardLevel >= 6) addAll(levelSix(positions))
            }
        }.also { require(it.isNotEmpty()) { "First fretboard question bank must not be empty" } }
    }

    private fun levelOne(
        positions: List<FretPosition>,
        settings: Settings,
    ): List<TrainingQuestion> = positions
        .groupBy { it.note }
        .toSortedMap(compareBy { NATURAL_NOTE_ORDER.indexOf(it) })
        .map { (note, targets) ->
            factory.createFretboardNoteSetQuestion(
                note = note,
                targets = targets,
                rangeId = settings.noteTrainingRangeId ?: "CUSTOM",
            )
        }

    private fun isGrowingFirstPosition(settings: Settings): Boolean =
        settings.noteTrainingRangeId == NoteTrainingRange.LOW_POSITION.name &&
            !settings.firstPositionComplete

    private fun levelTwo(positions: List<FretPosition>): List<TrainingQuestion> =
        relationQuestions(
            positions = positions,
            level = 2,
            relations = listOf(Relation("octave", "高八度", 12)),
            prompt = { anchor, relation ->
                "Lv.2 · 找到 ${anchor.note} 的${relation.label}"
            },
        )

    private fun levelThree(positions: List<FretPosition>): List<TrainingQuestion> =
        relationQuestions(
            positions = positions,
            level = 3,
            relations = listOf(
                Relation("half_step", "半音", 1),
                Relation("whole_step", "全音", 2),
            ),
            prompt = { anchor, relation ->
                "Lv.3 · ${anchor.note} 向上${relation.label}"
            },
        )

    private fun levelFour(positions: List<FretPosition>): List<TrainingQuestion> =
        relationQuestions(
            positions = positions,
            level = 4,
            relations = listOf(
                Relation("minor_second", "小二度", 1),
                Relation("major_second", "大二度", 2),
                Relation("minor_third", "小三度", 3),
                Relation("major_third", "大三度", 4),
                Relation("perfect_fourth", "纯四度", 5),
                Relation("perfect_fifth", "纯五度", 7),
            ),
            prompt = { anchor, relation ->
                "Lv.4 · 从 ${anchor.note} 向上${relation.label}"
            },
        )

    private fun levelFive(positions: List<FretPosition>): List<TrainingQuestion> = buildList {
        addAll(positions.mapNotNull { target ->
            val degree = GuitarCore.degreeFor(target.note) ?: return@mapNotNull null
            factory.createCurriculumQuestion(
                level = 5,
                kind = "c_major_degree",
                prompt = "Lv.5 · C 大调 · ${degree}级",
                anchor = null,
                target = target,
                relationId = "degree:$degree",
            )
        })
        addAll(
            namedSequenceQuestions(
                positions = positions,
                level = 5,
                kind = "c_major_ascending_fragment",
                noteNames = listOf("C", "D", "E"),
                prompt = "Lv.5 · 按顺序点 C→D→E（1→2→3）",
            ),
        )
    }

    private fun levelSix(positions: List<FretPosition>): List<TrainingQuestion> =
        namedSequenceQuestions(
            positions = positions,
            level = 6,
            kind = "c_major_triad",
            noteNames = listOf("C", "E", "G"),
            prompt = "Lv.6 · C 大三和弦 · 按顺序点 C→E→G",
        )

    private fun namedSequenceQuestions(
        positions: List<FretPosition>,
        level: Int,
        kind: String,
        noteNames: List<String>,
        prompt: String,
    ): List<TrainingQuestion> {
        val roots = positions.filter { it.note == noteNames.first() }
        return roots.mapNotNull { root ->
            val targets = mutableListOf(root)
            noteNames.drop(1).forEach { note ->
                val target = positions
                    .asSequence()
                    .filter { it.note == note && it !in targets }
                    .minWithOrNull(
                        compareBy<FretPosition>(
                            { kotlin.math.abs(it.string - root.string) +
                                kotlin.math.abs(it.fret - root.fret) },
                            { kotlin.math.abs(pitch(it) - pitch(root)) },
                            { it.string },
                            { it.fret },
                        ),
                    ) ?: return@mapNotNull null
                targets += target
            }
            val relationId = kind + ":root:s${root.string}:f${root.fret}"
            factory.createSequenceCurriculumQuestion(
                level = level,
                kind = kind,
                prompt = prompt,
                targets = targets,
                relationId = relationId,
            )
        }
    }

    private fun relationQuestions(
        positions: List<FretPosition>,
        level: Int,
        relations: List<Relation>,
        prompt: (FretPosition, Relation) -> String,
    ): List<TrainingQuestion> = relations.flatMap { relation ->
        positions.mapNotNull { anchor ->
            val target = positions
                .asSequence()
                .filter { pitch(it) - pitch(anchor) == relation.semitones }
                .minWithOrNull(
                    compareBy<FretPosition>(
                        { kotlin.math.abs(it.string - anchor.string) +
                            kotlin.math.abs(it.fret - anchor.fret) },
                        { it.string },
                        { it.fret },
                    ),
                )
                ?: return@mapNotNull null
            val relationId = relation.id +
                ":from:s${anchor.string}:f${anchor.fret}"
            factory.createCurriculumQuestion(
                level = level,
                kind = relation.id,
                prompt = prompt(anchor, relation),
                anchor = anchor,
                target = target,
                relationId = relationId,
            )
        }
    }

    private fun pitch(position: FretPosition): Int =
        OPEN_STRING_PITCH.getValue(position.string) + position.fret

    private data class Relation(
        val id: String,
        val label: String,
        val semitones: Int,
    )

    private companion object {
        val NATURAL_NOTE_ORDER = listOf("C", "D", "E", "F", "G", "A", "B")
        val OPEN_STRING_PITCH = mapOf(6 to 40, 5 to 45, 4 to 50, 3 to 55, 2 to 59, 1 to 64)
    }
}
