package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.IntervalTheory
import com.a3322505a.guitarlearning.storage.Settings

enum class IntervalName(
    val choiceId: String,
    val label: String,
    val semitones: Int,
    val degreeSpan: Int,
) {
    MINOR_SECOND("minor_second", "小二度", 1, 2),
    MAJOR_SECOND("major_second", "大二度", 2, 2),
    MINOR_THIRD("minor_third", "小三度", 3, 3),
    MAJOR_THIRD("major_third", "大三度", 4, 3),
    PERFECT_FOURTH("perfect_fourth", "纯四度", 5, 4),
    PERFECT_FIFTH("perfect_fifth", "纯五度", 7, 5),
    MINOR_SIXTH("minor_sixth", "小六度", 8, 6),
    MAJOR_SIXTH("major_sixth", "大六度", 9, 6),
    MINOR_SEVENTH("minor_seventh", "小七度", 10, 7),
    MAJOR_SEVENTH("major_seventh", "大七度", 11, 7),
    PERFECT_OCTAVE("perfect_octave", "纯八度", 12, 8),
    ;

    companion object {
        fun from(degreeSpan: Int, semitones: Int): IntervalName? =
            entries.singleOrNull {
                it.degreeSpan == degreeSpan && it.semitones == semitones
            }
    }
}

enum class IntervalLevel(
    val label: String,
    val subtitle: String,
    val lessonTitle: String,
    val lessonLines: List<String>,
) {
    LV0(
        "Lv.0  全音 / 半音",
        "先记住最基础距离",
        "全音 / 半音",
        listOf("半音 = 1 个半音距离", "全音 = 2 个半音", "E–F、B–C 是半音"),
    ),
    LV1(
        "Lv.1  二度",
        "1 / 2 个半音",
        "二度",
        listOf("小二度 = 1 个半音", "大二度 = 2 个半音"),
    ),
    LV2(
        "Lv.2  三度",
        "3 / 4 个半音",
        "三度",
        listOf("小三度 = 3 个半音", "大三度 = 4 个半音", "C D E 跨 3 个字母位置"),
    ),
    LV3(
        "Lv.3  四度 / 五度",
        "5 / 7 个半音",
        "四度 / 五度",
        listOf("纯四度 = 5 个半音", "纯五度 = 7 个半音"),
    ),
    LV4(
        "Lv.4  混合巩固",
        "不增加新内容",
        "混合巩固",
        listOf("这一关不增加新内容", "旧知识按你的掌握情况继续复习"),
    ),
    LV5(
        "Lv.5  六度",
        "8 / 9 个半音",
        "六度",
        listOf("小六度 = 8 个半音", "大六度 = 9 个半音"),
    ),
    LV6(
        "Lv.6  七度 / 八度",
        "10 / 11 / 12 个半音",
        "七度 / 八度",
        listOf("小七度 = 10 个半音", "大七度 = 11 个半音", "纯八度 = 12 个半音"),
    ),
    ;

    companion object {
        fun fromId(id: String?): IntervalLevel = entries.firstOrNull { it.name == id } ?: LV0
    }
}

class IntervalModule : TrainingModule {
    override val id: String = TrainingModuleIds.INTERVAL
    override val title: String = "全音 / 半音与音程"

    override fun buildQuestionBank(settings: Settings): List<TrainingQuestion> =
        buildQuestionBank(IntervalLevel.fromId(settings.intervalLevelId))

    fun buildQuestionBank(level: IntervalLevel): List<TrainingQuestion> = buildList {
        addAll(wholeHalfQuestions())
        if (level.ordinal >= IntervalLevel.LV1.ordinal) addAll(intervalQuestions(2))
        if (level.ordinal >= IntervalLevel.LV2.ordinal) addAll(intervalQuestions(3))
        if (level.ordinal >= IntervalLevel.LV3.ordinal) {
            addAll(intervalQuestions(4))
            addAll(intervalQuestions(5))
        }
        if (level.ordinal >= IntervalLevel.LV5.ordinal) addAll(intervalQuestions(6))
        if (level.ordinal >= IntervalLevel.LV6.ordinal) {
            addAll(intervalQuestions(7))
            addAll(intervalQuestions(8))
        }
    }

    private fun wholeHalfQuestions(): List<TrainingQuestion> =
        IntervalTheory.naturalNotes.mapIndexed { index, start ->
            val end = IntervalTheory.naturalNotes[(index + 1) % IntervalTheory.naturalNotes.size]
            val semitones = IntervalTheory.semitoneDistance(start, end)
            val correctId = if (semitones == 1) "half_step" else "whole_step"
            TrainingQuestion(
                moduleId = id,
                kind = "whole_half",
                prompt = "$start → $end",
                answerChoices = listOf(
                    AnswerChoice("whole_step", "全音"),
                    AnswerChoice("half_step", "半音"),
                ),
                correctChoiceId = correctId,
                knowledgeItemId = "interval:whole_half:$start:$end",
                payload = IntervalPayload(
                    startNote = start,
                    endNote = end,
                    semitoneDistance = semitones,
                    intervalName = null,
                    degreeSpan = null,
                ),
                weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            )
        }

    private fun intervalQuestions(span: Int): List<TrainingQuestion> =
        IntervalTheory.naturalNotes.mapNotNull { start ->
            val octave = span == 8
            val startIndex = IntervalTheory.naturalNotes.indexOf(start)
            val end = IntervalTheory.naturalNotes[(startIndex + span - 1) %
                IntervalTheory.naturalNotes.size]
            val semitones = IntervalTheory.semitoneDistance(start, end, octave)
            val interval = IntervalName.from(span, semitones) ?: return@mapNotNull null
            val idSuffix = if (octave) ":octave" else ""
            TrainingQuestion(
                moduleId = id,
                kind = "identify",
                prompt = if (octave) "$start → $end（高八度）" else "$start → $end",
                answerChoices = choicesFor(span),
                correctChoiceId = interval.choiceId,
                knowledgeItemId = "interval:identify:$start:$end$idSuffix",
                payload = IntervalPayload(
                    startNote = start,
                    endNote = end,
                    semitoneDistance = semitones,
                    intervalName = interval.label,
                    degreeSpan = span,
                    octave = octave,
                ),
                weightPolicy = QuestionWeightPolicy.BOUNDED_PER_ITEM,
            )
        }

    private fun choicesFor(span: Int): List<AnswerChoice> {
        val allowedSpans = when (span) {
            2 -> setOf(2)
            3 -> setOf(3)
            4, 5 -> setOf(4, 5)
            6 -> setOf(6)
            7, 8 -> setOf(7, 8)
            else -> error("Unsupported interval span")
        }
        return IntervalName.entries
            .filter { it.degreeSpan in allowedSpans }
            .map { AnswerChoice(it.choiceId, it.label) }
    }
}
