package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.LevelProgress
import com.a3322505a.guitarlearning.storage.Settings

object FretboardLevelRules {
    const val MINIMUM_ATTEMPTS = 20
    const val REQUIRED_CORRECT = 18
    const val MAXIMUM_LEVEL = 5

    fun record(progress: LevelProgress, isCorrect: Boolean): LevelProgress =
        progress.copy(recentResults = (progress.recentResults + isCorrect).takeLast(20))

    fun qualifies(progress: LevelProgress): Boolean =
        progress.attempts == MINIMUM_ATTEMPTS && progress.correct >= REQUIRED_CORRECT
}

/** Lv.1–Lv.5 single-target curriculum for turning theory into fretboard locations. */
class FirstFretboardModule : TrainingModule {
    override val id: String = TrainingModuleIds.FRET_NOTE
    override val title: String = "第一指板"
    private val factory = QuestionFactory()

    override fun buildQuestionBank(settings: Settings): List<TrainingQuestion> {
        val positions = GuitarCore.allPositions(
            strings = settings.selectedStrings.sorted(),
            frets = settings.fretStart..settings.fretEnd,
            naturalOnly = true,
        )
        return buildList {
            addAll(levelOne(positions, settings))
            if (settings.unlockedFretboardLevel >= 2) addAll(levelTwo(positions))
            if (settings.unlockedFretboardLevel >= 3) addAll(levelThree(positions))
            if (settings.unlockedFretboardLevel >= 4) addAll(levelFour(positions))
            if (settings.unlockedFretboardLevel >= 5) addAll(levelFive(positions))
        }.also { require(it.isNotEmpty()) { "First fretboard question bank must not be empty" } }
    }

    private fun levelOne(
        positions: List<FretPosition>,
        settings: Settings,
    ): List<TrainingQuestion> = positions.map {
        factory.create(
            type = QuestionType.FretToNote,
            position = it,
            disambiguateOctave = settings.fretStart == 0 && settings.fretEnd == 12,
        )
    }

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

    private fun levelFive(positions: List<FretPosition>): List<TrainingQuestion> =
        positions.mapNotNull { target ->
            val degree = GuitarCore.degreeFor(target.note) ?: return@mapNotNull null
            factory.createCurriculumQuestion(
                level = 5,
                kind = "c_major_degree",
                prompt = "Lv.5 · C 大调 · ${degree}级",
                anchor = null,
                target = target,
                relationId = "degree:$degree",
            )
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
        val OPEN_STRING_PITCH = mapOf(6 to 40, 5 to 45, 4 to 50, 3 to 55, 2 to 59, 1 to 64)
    }
}
