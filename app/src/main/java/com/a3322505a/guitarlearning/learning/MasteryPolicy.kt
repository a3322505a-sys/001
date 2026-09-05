package com.a3322505a.guitarlearning.learning

object MasteryPolicy {
    fun independent(state: LearnerState, active: ActiveTask, ordinal: Int): Boolean {
        val t = active.task
        if (t.guided || active.hintLevel > 0 || active.firstCorrect == null) return false
        val viewedAt = t.coordinate?.let { state.viewedPositions[it.id] }
        if (viewedAt != null && ordinal - viewedAt < 3) return false
        // An answer shown immediately beforehand is exposure, not independent recall.
        val previous = state.attempts.lastOrNull { a -> a.task.id != t.id && (
            a.task.skillId == t.skillId || (t.coordinate != null && a.task.coordinate == t.coordinate && (a.task.guided || a.hintLevel > 0))) }
        if (t.nodeId in listOf("g00", "n00", "tab01")) return previous == null || !previous.task.guided || ordinal - previous.ordinal >= 2
        return previous == null || ordinal - previous.ordinal >= 3
    }

    fun positionEvidence(state: LearnerState, c: Coordinate): List<Attempt> = state.attempts.filter {
        it.independent && it.task.coordinate == c && it.task.direction in listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE)
    }

    fun positionPassed(state: LearnerState, c: Coordinate): Boolean {
        val recent = positionEvidence(state, c).takeLast(6)
        return recent.size == 6 && recent.count { it.firstCorrect == true } >= 5 &&
            listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE).all { direction ->
                val answers = recent.filter { it.task.direction == direction }
                answers.size >= 2 && answers.lastOrNull()?.firstCorrect == true
            }
    }

    fun passed(state: LearnerState, node: CurriculumNode): Boolean {
        val good = state.attempts.filter { it.task.nodeId == node.id && it.independent && it.firstCorrect == true }
        return when (node.id) {
            "g00" -> listOf("strings", "frets", "markers").all { group -> good.filter { it.task.skillId.startsWith("g00:$group:") }.map { it.task.skillId }.distinct().size >= 2 }
            "n00" -> good.mapNotNull { it.task.constraint.symbol }.toSet().containsAll(listOf("E", "F"))
            "tab01" -> listOf(Coordinate(1, 0), Coordinate(1, 1)).all { c -> good.count { it.task.coordinate == c } >= 2 }
            else -> node.positions.isNotEmpty() && node.positions.all { positionPassed(state, it) }
        }
    }

    fun update(state: LearnerState, now: Long, day: String): LearnerState {
        val updated = state.progress.toMutableMap()
        Curriculum.nodes.filter { it.implemented && Curriculum.available(state, it) }.forEach { node ->
            val old = updated[node.id] ?: NodeProgress()
            val pass = passed(state, node)
            val latest = state.attempts.lastOrNull { it.task.nodeId == node.id && it.independent }
            val failureAfterMastery = old.masteredAt != null && latest != null && latest.at >= old.masteredAt && latest.firstCorrect == false
            val recentGood = old.masteredAt != null && latest?.firstCorrect == true && pass
            val masteredAt = old.masteredAt ?: if (pass) now else null
            val initialDay = state.attempts.lastOrNull { it.at <= (old.masteredAt ?: now) }?.localDay
            val retention = old.masteredAt != null && latest?.firstCorrect == true && latest.localDay != initialDay && day == latest.localDay &&
                node.positions.all { c -> state.attempts.any { it.independent && it.task.coordinate == c && it.firstCorrect == true && it.localDay == day && it.at > old.masteredAt } }
            updated[node.id] = NodeProgress(masteredAt, if (retention) day else old.retainedOn,
                if (failureAfterMastery) true else if (recentGood) false else old.needsReview)
        }
        return state.copy(progress = updated)
    }
}
