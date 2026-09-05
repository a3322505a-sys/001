package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

/** Fixed solfege and key-relative degrees deliberately have separate evidence families. */
object MappingLessons {
    val notes = listOf("C", "D", "E", "F", "G", "A", "B")
    val fixedDirections = listOf(Direction.NOTE_TO_SOLFEGE, Direction.SOLFEGE_TO_NOTE)
    val degreeDirections = listOf(Direction.NOTE_TO_DEGREE, Direction.DEGREE_TO_NOTE)
    val directions = fixedDirections + degreeDirections

    fun family(task: LearningTask): String? = task.mappingNote?.let { note ->
        when (task.direction) {
            in fixedDirections -> "mapping:fixed:$note"
            in degreeDirections -> "mapping:major:${task.tonicPitchClass}:$note"
            else -> null
        }
    }

    fun sameFact(a: LearningTask, b: LearningTask): Boolean = family(a)?.let { it == family(b) } ?: false

    fun evidence(state: LearnerState, note: String, degrees: Boolean): List<Attempt> = state.attempts.filter {
        it.independent && it.task.mappingNote == note &&
            it.task.direction in (if (degrees) degreeDirections else fixedDirections) &&
            (!degrees || it.task.tonicPitchClass == 0)
    }

    fun pairPassed(state: LearnerState, note: String, degrees: Boolean): Boolean {
        val recent = evidence(state, note, degrees).takeLast(6)
        return recent.size == 6 && recent.count { it.firstCorrect == true } >= 5 &&
            (if (degrees) degreeDirections else fixedDirections).all { d ->
                val answers = recent.filter { it.task.direction == d }
                answers.size >= 2 && answers.lastOrNull()?.firstCorrect == true
            }
    }

    fun passed(state: LearnerState): Boolean = listOf(false, true).all { degrees -> notes.all { pairPassed(state, it, degrees) } }

    fun retained(state: LearnerState, day: String, masteredAt: Long): Boolean = notes.all { note -> directions.all { d ->
        state.attempts.any { it.independent && it.firstCorrect == true && it.task.mappingNote == note &&
            it.task.direction == d && (d !in degreeDirections || it.task.tonicPitchClass == 0) && it.localDay == day && it.at > masteredAt }
    } }

    fun next(state: LearnerState, source: TaskSource, random: Random): LearningTask {
        val degrees = notes.all { pairPassed(state, it, false) } &&
            (!notes.all { pairPassed(state, it, true) } || random.nextBoolean())
        val pair = if (degrees) degreeDirections else fixedDirections
        notes.firstOrNull { introId(it, degrees) !in state.introductions }?.let { note ->
            return make(note, pair.first(), TaskSource.DEMONSTRATION).copy(introductionId = introId(note, degrees))
        }
        val pending = notes.filter { !pairPassed(state, it, degrees) }.ifEmpty { notes }
        val choices = pending.flatMap { note -> pair.map { note to it } }
        val last = state.attempts.lastOrNull()?.task
        val spaced = choices.filter { (note, d) ->
            val recent = state.attempts.lastOrNull { it.task.mappingNote == note && it.task.direction == d }
            recent == null || state.attempts.size + 1 - recent.ordinal >= 3
        }.ifEmpty { choices.filter { it.first != last?.mappingNote || it.second != last.direction }.ifEmpty { choices } }
        val selected = spaced.shuffled(random).minBy { (note, direction) ->
            evidence(state, note, degrees).count { it.task.direction == direction && it.firstCorrect == true }
        }
        return make(selected.first, selected.second, source)
    }

    private fun introId(note: String, degrees: Boolean) = if (degrees) "mapping:major:0:$note:intro" else "mapping:fixed:$note:intro"

    fun make(note: String, direction: Direction, source: TaskSource, tonic: Int = 0): LearningTask {
        require(direction in directions)
        val degrees = direction in degreeDirections
        val solfege = MusicFacts.fixedSolfege.getValue(note)
        val degree = if (degrees) requireNotNull(MusicFacts.majorDegree(MusicFacts.noteNames.indexOf(note), tonic)) else null
        val key = "${MusicFacts.noteNames[tonic]} 大调"
        val reverse = direction == Direction.SOLFEGE_TO_NOTE || direction == Direction.DEGREE_TO_NOTE
        val target = if (reverse) note else if (degrees) degree.toString() else solfege
        val prompt = when (direction) {
            Direction.NOTE_TO_SOLFEGE -> "$note 的固定唱名是什么？"
            Direction.SOLFEGE_TO_NOTE -> "固定唱名 $solfege 对应哪个音名？"
            Direction.NOTE_TO_DEGREE -> "$key：$note 是第几级？"
            else -> "$key：第 $degree 级是什么音名？"
        }
        return LearningTask(nodeId = "mapping", skillId = "mapping:${if (degrees) "major:$tonic" else "fixed"}:$note:${direction.name}",
            direction = direction, prompt = prompt,
            explanation = if (degrees) "$key 的主音是${MusicFacts.noteNames[tonic]}；$note 在这个调里是第 $degree 级。换调后级数会改变。"
                else "$note 是音名，固定唱名是 $solfege。固定唱名跟音名对应，和调内级数分开。",
            constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = target), source = source,
            options = if (reverse) notes else if (degrees) (1..7).map { it.toString() } else notes.map { MusicFacts.fixedSolfege.getValue(it) },
            mappingNote = note, tonicPitchClass = if (degrees) tonic else null, tonalMode = if (degrees) "major" else null)
    }

    fun directionLabel(direction: Direction): String = when (direction) {
        Direction.NOTE_TO_SOLFEGE -> "音名→唱名"
        Direction.SOLFEGE_TO_NOTE -> "唱名→音名"
        Direction.NOTE_TO_DEGREE -> "音名→级数（C 大调）"
        Direction.DEGREE_TO_NOTE -> "级数→音名（C 大调）"
        else -> direction.name
    }
}
