package com.a3322505a.guitarlearning.learning

/** Seven answers are a response contract, not a claim that seven positions are mastered. */
object NaturalRecognition {
    val options = listOf("C", "D", "E", "F", "G", "A", "B")
    fun current(t: LearningTask) = t.direction != Direction.POSITION_TO_NOTE ||
        t.adaptive?.options?.isNotEmpty() == true || t.options.size == 7 && t.options.toSet() == options.toSet()
    fun normalize(t: LearningTask): LearningTask = if (t.direction == Direction.POSITION_TO_NOTE &&
        t.coordinate != null && t.constraint.symbol in options && t.adaptive?.options.isNullOrEmpty())
        t.copy(options = options) else t

    // Only unanswered snapshots can change conditions. Completed/partly answered facts stay exact.
    fun resume(s: LearnerState): LearnerState {
        val observations = s.responseObservations.toMutableMap()
        fun active(a: ActiveTask?, session: String?): ActiveTask? = a?.let {
            if (it.phase != Phase.ANSWERING || it.inputs.isNotEmpty() || it.firstCorrect != null) it else {
                val task = normalize(it.task)
                if (task != it.task && session != null) observations[task.id] =
                    ResponseObservation(task, session, quality = TimingQuality.INTERRUPTED)
                it.copy(task = task)
            }
        }
        fun suspended(p: SuspendedLesson?) = p?.copy(active = active(p.active,p.sessionId))
        fun paused(p: PausedTraining) = p.copy(active = active(p.active,p.sessionId),
            suspendedLesson = suspended(p.suspendedLesson), pilotSuspended = suspended(p.pilotSuspended))
        val updated = s.copy(active = active(s.active,s.sessionId),
            suspendedLesson = suspended(s.suspendedLesson), pilotSuspended = suspended(s.pilotSuspended),
            pausedRegions = s.pausedRegions.mapValues { paused(it.value) }, pausedTraining = s.pausedTraining?.let(::paused))
        return updated.copy(responseObservations = observations.toMap())
    }
}

/** Trial policy v1: four fast first answers, two positions and both directions; no mastery writes. */
object RegionProgression {
    private fun good(s: LearnerState, a: Attempt, fast: Boolean = true) = ExperiencePolicy.plain(a.task) && NaturalRecognition.current(a.task) &&
        a.completed && ResponseTiming.timely(s, a) && s.responseObservations[a.task.id]?.replayed == false &&
        (!fast || (s.responseObservations[a.task.id]?.durationMs ?: Long.MAX_VALUE) <= ExperiencePolicy.fast(a.task.direction))
    fun quick(s: LearnerState): Boolean {
        val recent = s.attempts.filter { it.sessionId == s.sessionId && !it.task.guided &&
            it.task.direction in AdaptiveEvidence.positionDirections && it.task.adaptive?.scaffolded != true }.takeLast(4)
        return recent.size == 4 && recent.all { good(s, it) } && recent.map { it.task.coordinate }.distinct().size >= 2 &&
            recent.map { it.task.direction }.distinct().size == 2
    }
    fun available(s: LearnerState, n: CurriculumNode): Boolean {
        if (Curriculum.available(s, n)) return true
        if (n.category != Category.FRETBOARD || !n.implemented) return false
        return n.prerequisites.all { id ->
            val p = Curriculum.node(id)
            Curriculum.mastered(s, id) || if (p.positions.isNotEmpty())
                available(s, p) && p.positions.all { AdaptiveEvidence.positionTarget(it) in s.introductions } &&
                    (id != "p09" || middleTrial(s)) else false
        }
    }
    fun middleTrial(s: LearnerState): Boolean {
        val positions = AdaptiveEvidence.targets(FretboardRegion.LOW)
        val latest = s.attempts.filter { it.task.coordinate in positions && ExperiencePolicy.plain(it.task) }
            .groupBy { AdaptiveEvidence.unit(it.task) }.mapValues { it.value.last() }
        val mostRecent = s.attempts.maxOfOrNull { it.at } ?: return false
        return RegionProtection.active(s).none { it.original.coordinate in positions } && positions.all { c ->
            AdaptiveEvidence.positionTarget(c) in s.introductions && AdaptiveEvidence.positionDirections.all { d ->
                latest[AdaptiveEvidence.positionUnit(c,d)]?.let { good(s,it,false) && mostRecent-it.at <= AdaptiveEvidence.WINDOW_MS } == true
            }
        }
    }
    fun introduce(s: LearnerState, scheduler: LessonScheduler): LearningTask? {
        val region = s.regionTraining?.regionId ?: return null
        val n = RegionTraining.nodes(region).firstOrNull { available(s,it) && it.positions.any { c -> AdaptiveEvidence.positionTarget(c) !in s.introductions } } ?: return null
        val c = n.positions.first { AdaptiveEvidence.positionTarget(it) !in s.introductions }
        return scheduler.makePosition(n.id,c,Direction.NOTE_TO_POSITION,TaskSource.DEMONSTRATION)
            .copy(introductionId = AdaptiveEvidence.positionTarget(c), adaptive = AdaptiveTask("trial-v1:$region",PracticePurpose.NEXT))
    }
    fun next(s: LearnerState, scheduler: LessonScheduler, now: Long): LearningTask? {
        val region = s.regionTraining?.regionId ?: return null
        val known = RegionTraining.known(s,region).distinctBy { it.second }
        val pending = RegionProtection.active(s)
        // A third taught target makes two distinct intervening responses possible in small pools.
        if (known.size < (if (pending.isEmpty()) 2 else 3)) return introduce(s,scheduler)
        val lastNew = s.attempts.lastOrNull { it.task.adaptive?.purpose == PracticePurpose.NEXT &&
            it.task.guided && it.task.direction in AdaptiveEvidence.positionDirections &&
            it.task.coordinate != null && known.any { p -> p.second == it.task.coordinate } }
        if (lastNew != null && pending.none { it.original.coordinate == lastNew.task.coordinate }) {
            val verified = s.attempts.filter { it.ordinal > lastNew.ordinal && it.task.coordinate == lastNew.task.coordinate }
            val missing = AdaptiveEvidence.positionDirections.firstOrNull { d -> verified.none { it.task.direction == d && good(s,it,false) } }
            if (missing != null) {
                val task = scheduler.makePosition(lastNew.task.nodeId,requireNotNull(lastNew.task.coordinate),missing,TaskSource.MAIN)
                    .copy(adaptive=AdaptiveTask("trial-v1:$region",PracticePurpose.COVERAGE))
                // Validate each new point in both directions after real intervening responses.
                // Pure random coverage can leave one direction missing across many short rounds.
                return task.takeIf { AdaptiveEvidence.View(s,now).eligible(it) }
            }
        }
        if (!quick(s)) return null
        return introduce(s,scheduler)
    }
    fun summary(s: LearnerState): String {
        val attempts = s.attempts.filter { it.sessionId == s.sessionId }
        val new = attempts.filter { it.completed && it.task.introductionId != null }.mapNotNull { it.task.coordinate }.distinct().size
        val covered = attempts.mapNotNull { it.task.coordinate }.distinct().size
        val pending = RegionProtection.active(s).map { it.original.coordinate }.distinct().size
        return "本轮练了${covered}个位置" + (if(new>0) "，加入${new}个新点" else "") +
            (if(pending>0) "；${pending}个位置继续巩固。" else if(quick(s)) "；快准表现已支持继续试练。" else "；继续穿插复习，快且准时扩大范围。")
    }
}
