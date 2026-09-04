package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.LevelProgress
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

data class FretboardKnowledgeScope(
    val trainingRange: List<FretPosition>,
    val activeKnowledge: List<FretPosition>,
) {
    fun requiredSet(note: String): List<FretPosition> = activeKnowledge.filter { it.note == note }
    fun correctUniverse(note: String): List<FretPosition> = trainingRange.filter { it.note == note }
}

object FirstPositionCurriculum {
    val baselineNotes = listOf("E", "A", "D", "G", "B")
    val baselinePositions: List<FretPosition> = listOf(
        6 to 0,
        5 to 0,
        4 to 0,
        3 to 0,
        2 to 0,
        1 to 0,
    ).map { (string, fret) -> GuitarCore.getFretPosition(string, fret) }

    /** Stable one-coordinate-at-a-time order; it never unlocks a complete fret in one step. */
    val expansionPositions: List<FretPosition> = listOf(
        6 to 1,
        2 to 1,
        1 to 1,
        5 to 2,
        4 to 2,
        3 to 2,
        6 to 3,
        5 to 3,
        4 to 3,
        2 to 3,
        1 to 3,
        3 to 4,
    ).map { (string, fret) -> GuitarCore.getFretPosition(string, fret) }

    fun id(position: FretPosition): String = "s${position.string}f${position.fret}"

    fun activePositions(settings: Settings): List<FretPosition> =
        baselinePositions + expansionPositions.filter { id(it) in settings.firstPositionActiveKnowledgeIds }

    fun nextInactive(settings: Settings): FretPosition? =
        expansionPositions.firstOrNull { id(it) !in settings.firstPositionActiveKnowledgeIds }
}

object FirstPositionScaleRoutes {
    val cMajorAscending: List<FretPosition> = listOf(
        5 to 3,
        4 to 0,
        4 to 2,
        4 to 3,
        3 to 0,
        3 to 2,
        2 to 0,
        2 to 1,
    ).map { (string, fret) -> GuitarCore.getFretPosition(string, fret) }

    val cMajorDescending: List<FretPosition> = cMajorAscending.reversed()
}

data class FirstPositionChordShape(
    val id: String,
    val label: String,
    val rootNote: String,
    val chordNotes: Set<String>,
    val positions: List<FretPosition>,
)

object FirstPositionChordShapes {
    val ordered: List<FirstPositionChordShape> = listOf(
        shape("c", "C 和弦", "C", setOf("C", "E", "G"), 5 to 3, 4 to 2, 3 to 0, 2 to 1, 1 to 0),
        shape("g", "G 和弦", "G", setOf("G", "B", "D"), 6 to 3, 5 to 2, 4 to 0, 3 to 0, 2 to 0, 1 to 3),
        shape("am", "Am 和弦", "A", setOf("A", "C", "E"), 5 to 0, 4 to 2, 3 to 2, 2 to 1, 1 to 0),
        shape("em", "Em 和弦", "E", setOf("E", "G", "B"), 6 to 0, 5 to 2, 4 to 2, 3 to 0, 2 to 0, 1 to 0),
        shape("f", "F 和弦", "F", setOf("F", "A", "C"), 6 to 1, 5 to 3, 4 to 3, 3 to 2, 2 to 1, 1 to 1),
    )

    private fun shape(
        id: String,
        label: String,
        rootNote: String,
        chordNotes: Set<String>,
        vararg coordinates: Pair<Int, Int>,
    ) = FirstPositionChordShape(
        id = id,
        label = label,
        rootNote = rootNote,
        chordNotes = chordNotes,
        positions = coordinates.map { (string, fret) -> GuitarCore.getFretPosition(string, fret) },
    )
}

/** Lv.1 note sets plus the existing Lv.2–Lv.6 fretboard curriculum. */
class FirstFretboardModule : TrainingModule {
    override val id: String = TrainingModuleIds.FRET_NOTE
    override val title: String = "第一指板"
    private val factory = QuestionFactory()

    override fun buildQuestionBank(settings: Settings): List<TrainingQuestion> {
        val trainingRange = GuitarCore.allPositions(
            strings = settings.selectedStrings.sorted(),
            frets = settings.fretStart..settings.fretEnd,
            naturalOnly = true,
        )
        val activeKnowledge = if (isGrowingFirstPosition(settings)) {
            FirstPositionCurriculum.activePositions(settings)
        } else {
            trainingRange
        }
        val scope = FretboardKnowledgeScope(trainingRange, activeKnowledge)
        return buildList {
            addAll(levelOne(scope, settings))
            if (!isGrowingFirstPosition(settings)) {
                if (settings.unlockedFretboardLevel >= 2) addAll(levelTwo(trainingRange))
                if (settings.unlockedFretboardLevel >= 3) addAll(levelThree(trainingRange))
                if (settings.unlockedFretboardLevel >= 4) addAll(levelFour(trainingRange))
                if (settings.unlockedFretboardLevel >= 5) addAll(levelFive(trainingRange))
                if (
                    settings.unlockedFretboardLevel >= 6 &&
                    trainingRange.containsAll(firstPositionNaturals)
                ) {
                    addAll(levelSix(settings))
                }
            }
        }.also { require(it.isNotEmpty()) { "First fretboard question bank must not be empty" } }
    }

    private fun levelOne(
        scope: FretboardKnowledgeScope,
        settings: Settings,
    ): List<TrainingQuestion> = scope.activeKnowledge
        .groupBy { it.note }
        .toSortedMap(
            compareBy<String> { note ->
                if (!settings.firstPositionBaselineComplete) {
                    FirstPositionCurriculum.baselineNotes.indexOf(note)
                } else {
                    NATURAL_NOTE_ORDER.indexOf(note)
                }
            }.thenBy { it },
        )
        .map { (note, targets) ->
            factory.createFretboardNoteSetQuestion(
                note = note,
                targets = targets,
                rangeId = settings.noteTrainingRangeId ?: "CUSTOM",
                correctUniverse = scope.correctUniverse(note),
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
        if (positions.containsAll(FirstPositionScaleRoutes.cMajorAscending)) {
            add(
                factory.createSequenceCurriculumQuestion(
                    level = 5,
                    kind = "c_major_scale_ascending",
                    prompt = "Lv.5 · C 大调音阶上行 · C→D→E→F→G→A→B→C′",
                    targets = FirstPositionScaleRoutes.cMajorAscending,
                    relationId = "c_major_scale:ascending",
                ),
            )
            add(
                factory.createSequenceCurriculumQuestion(
                    level = 5,
                    kind = "c_major_scale_descending",
                    prompt = "Lv.5 · C 大调音阶下行 · C′→B→A→G→F→E→D→C",
                    targets = FirstPositionScaleRoutes.cMajorDescending,
                    relationId = "c_major_scale:descending",
                ),
            )
        }
    }

    private fun levelSix(settings: Settings): List<TrainingQuestion> {
        val firstPosition = GuitarCore.allPositions(frets = 0..4, naturalOnly = true)
        val toneSequences = listOf(
            ChordToneSequence("c_major_triad", "C 大三和弦", listOf("C", "E", "G")),
            ChordToneSequence("g_major_triad", "G 大三和弦", listOf("G", "B", "D")),
            ChordToneSequence("a_minor_triad", "Am 小三和弦", listOf("A", "C", "E")),
            ChordToneSequence("e_minor_triad", "Em 小三和弦", listOf("E", "G", "B")),
            ChordToneSequence("f_major_triad", "F 大三和弦", listOf("F", "A", "C")),
        ).flatMap { chord ->
            namedSequenceQuestions(
                positions = firstPosition,
                level = 6,
                kind = chord.kind,
                noteNames = chord.notes,
                prompt = "Lv.6 · ${chord.label} · 按顺序点 ${chord.notes.joinToString("→")}",
            )
        }
        val shapes = FirstPositionChordShapes.ordered
            .take(settings.unlockedChordShapeCount)
            .mapIndexed { index, shape ->
                factory.createFretboardShapeQuestion(
                    chordId = shape.id,
                    chordLabel = shape.label,
                    rootNote = shape.rootNote,
                    order = index + 1,
                    targets = shape.positions,
                )
            }
        return toneSequences + shapes
    }

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

    private data class ChordToneSequence(
        val kind: String,
        val label: String,
        val notes: List<String>,
    )

    private companion object {
        val NATURAL_NOTE_ORDER = listOf("C", "D", "E", "F", "G", "A", "B")
        val OPEN_STRING_PITCH = mapOf(6 to 40, 5 to 45, 4 to 50, 3 to 55, 2 to 59, 1 to 64)
        val firstPositionNaturals = GuitarCore.allPositions(frets = 0..4, naturalOnly = true)
    }
}
