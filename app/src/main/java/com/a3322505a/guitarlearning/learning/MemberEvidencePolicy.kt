package com.a3322505a.guitarlearning.learning

/** Records only a member the learner actually attempted. Partial answers never invent other results. */
object MemberEvidencePolicy {
    fun record(state: LearnerState, active: ActiveTask, old: Attempt?, result: ClickResult, coordinate: Coordinate?, now: Long): List<TargetEvidence> {
        val task = active.task
        val members = old?.members.orEmpty()
        if (task.completion != CompletionKind.SEQUENCE || task.targetSkillIds.size != task.sequence.size) return members
        val index = active.sequenceIndex
        val skill = task.targetSkillIds.getOrNull(index) ?: return members
        val previous = members.firstOrNull { it.index == index }
        val member = if (previous != null) previous.copy(completed = previous.completed || result != ClickResult.WRONG, coordinate = if (result != ClickResult.WRONG) coordinate else previous.coordinate)
        else TargetEvidence(index, skill, task.direction, if (result != ClickResult.WRONG) coordinate else null, result != ClickResult.WRONG,
            independent(state, active, skill), now, result != ClickResult.WRONG,
            firstUnassisted = !task.guided && active.hintLevel == 0 && !active.hintRequested && active.phase == Phase.ANSWERING)
        return if (previous == null) members + member else members.map { if (it.index == index) member else it }
    }

    private fun independent(state: LearnerState, active: ActiveTask, skill: String): Boolean {
        // A reduced phrase diagnoses its members; it does not pass the original full task.
        if (active.task.adaptive?.familyScope != null && active.task.adaptive.stage == 0) return false
        if (active.task.guided || active.hintLevel > 0 || active.phase != Phase.ANSWERING) return false
        val viewed = active.task.chord?.let { state.viewedSkills["chord:${it.id}"] }
        if (viewed != null && state.attempts.count { it.ordinal > viewed } < 3) return false
        val units = state.attempts.flatMap { a -> if (a.members.isEmpty()) listOf(a to a.task.skillId) else a.members.map { a to it.skillId } }
        val last = units.indexOfLast { (attempt, key) -> key == skill ||
            (attempt.task.nodeId == active.task.nodeId && (attempt.task.guided || attempt.hintLevel > 0)) }
        return last < 0 || units.size - last >= 3
    }

    fun evidence(state: LearnerState, skill: String): List<TargetEvidence> = state.attempts.flatMap { it.members }.filter { it.skillId == skill && it.independent }
    fun skillPassed(state: LearnerState, skill: String): Boolean = evidence(state, skill).takeLast(3).let { recent ->
        recent.size == 3 && recent.all { it.firstCorrect && it.completed }
    }
    fun retained(state: LearnerState, skills: List<String>, day: String, masteredAt: Long): Boolean = skills.isNotEmpty() && skills.all { skill ->
        state.attempts.any { a -> a.localDay == day && a.members.any { it.skillId == skill && it.independent && it.firstCorrect && it.at > masteredAt } }
    }
}
