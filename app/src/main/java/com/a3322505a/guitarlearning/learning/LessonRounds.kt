package com.a3322505a.guitarlearning.learning

/** Derived from saved task identities: no new profile format or synthetic mastery. */
object LessonRounds {
    const val SIZE = 12
    fun active(s: LearnerState) = s.sessionId != null && s.regionTraining == null && s.practice == null && s.pilot == null
    fun issued(s: LearnerState) = (s.attempts.filter { it.sessionId == s.sessionId }.map { it.task.id } + listOfNotNull(s.active?.task?.id)).distinct()
    fun finished(s: LearnerState) = active(s) && (issued(s).size >= SIZE ||
        !s.reviewMode && Curriculum.mastered(s, s.currentNode))
    fun summary(s: LearnerState): String {
        val count = s.attempts.filter { it.sessionId == s.sessionId && it.completed }.map { it.task.id }.distinct().size
        val result = if (!s.reviewMode && Curriculum.mastered(s, s.currentNode)) "本课已达标。" else "本轮完成。"
        return "$result 已完成 $count 个任务，进度已保存。"
    }
    fun nextNode(s: LearnerState): String = if (!s.reviewMode && Curriculum.mastered(s, s.currentNode))
        Curriculum.next(s)?.id ?: s.currentNode else s.currentNode
}
