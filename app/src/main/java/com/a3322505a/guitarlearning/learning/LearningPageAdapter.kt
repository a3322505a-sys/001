package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.BuildConfig
import com.a3322505a.guitarlearning.core.MusicFacts
import com.a3322505a.guitarlearning.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class HomeGroup(val title: String, val description: String, val categories: Set<Category>) {
    INTRO("吉他入门", "认识吉他 · 基础认识 · 读谱入门", setOf(Category.GUITAR, Category.SYMBOL, Category.READING)),
    FRETBOARD("指板训练", "音位练习与复习", setOf(Category.FRETBOARD)),
    ADVANCED("进阶应用", "和弦指法与结构学习", setOf(Category.ADVANCED)),
}

/** Thin projections of the existing curriculum/evidence policies; no second mastery calculation. */
internal object LearningPageAdapter {
    fun row(s: LearnerState, n: CurriculumNode): NodeRowUi {
        val status = nodeVisualState(s, n)
        val available = Curriculum.available(s, n)
        val current = s.sessionId != null && s.currentNode == n.id && available
        return NodeRowUi(n.id, n.title, status, current,
            if (status == NodeVisualState.MASTERED && n.id == "g00") "已认识" else if (current && s.practice != null) "专项练习中" else status.label,
            if (available) if (Curriculum.mastered(s, n.id)) "复习" else if (current) "继续" else "学习" else null,
            n.prerequisites.filterNot { Curriculum.mastered(s, it) }.joinToString("、") { Curriculum.node(it).title })
    }
    fun home(s: LearnerState): List<HomeEntryUi> {
        val current = if (s.sessionId != null) Curriculum.node(s.currentNode) else Curriculum.next(s)
        return HomeGroup.entries.map { group ->
            val active = current?.takeIf { it.category in group.categories }
            HomeEntryUi(group.name, group.title, active?.let { "当前：${it.title}" } ?: group.description,
                if (s.regionTraining != null) "继续训练" else if (s.practice != null) "继续专项" else if (s.sessionId != null) "继续学习" else "开始学习", active?.id, s.sessionId != null)
        } + HomeEntryUi("tree", "知识树", "按能力查看学习进展")
    }
    fun catalog(s: LearnerState, categories: Set<Category>, examples: Boolean = false) = CatalogUiState(categories.map { category ->
        if (category == Category.FRETBOARD) CatalogSectionUi(null, regions = FretboardRegion.entries.map { region ->
            RegionUi(region.name, "${region.title} · ${region.rangeLabel}", region.progressLabel(s), emptyList(), emptyList(),
                if (!RegionTraining.available(s, region.name)) "完成前置内容后开始" else if (region == FretboardRegion.MIDDLE && s.middleRecommendedAt != null) "推荐下一步：加入两个中把位新音，穿插低把位复习" else null,
                if (RegionTraining.available(s, region.name)) if (RegionSessions.active(s) && s.regionTraining?.regionId == region.name && s.active != null || s.pausedRegions[region.name]?.active != null) "继续" else "开始" else null)
        }) else CatalogSectionUi(category.title.takeIf { categories.size > 1 }, Curriculum.nodes.filter { it.category == category }.map { row(s, it) })
    }, examples)
    fun tree(s: LearnerState) = CapabilityGroups.all().map { capabilityRow(s, it) }
    private fun capabilityRow(s: LearnerState, group: CapabilityGroup): NodeRowUi {
        val node = CapabilityGroups.next(s, group)
        val base = row(s, node)
        val available = group.nodeIds.any { Curriculum.available(s, Curriculum.node(it)) }
        val historical = group.nodeIds.all { Curriculum.mastered(s, it) }
        val region = group.region
        return base.copy(title = group.title,
            status = if (!available) NodeVisualState.LOCKED else if (base.current) NodeVisualState.CURRENT else if (historical && region == null) NodeVisualState.MASTERED else NodeVisualState.AVAILABLE,
            statusLabel = region?.progressLabel(s) ?: if (historical) "已学过 · 可复习" else base.statusLabel,
            prerequisites = node.prerequisites.filterNot { Curriculum.mastered(s, it) }.map { CapabilityGroups.forNode(it).title }.distinct().joinToString("、"))
    }
    fun node(s: LearnerState, n: CurriculumNode): NodeDetailUiState {
        val group = CapabilityGroups.forNode(n.id)
        val next = CapabilityGroups.next(s, group)
        val history = InfoPanelUi("历史课程", lines = listOf("已通过 ${group.nodeIds.count { Curriculum.mastered(s, it) }}/${group.nodeIds.size}"))
        return NodeDetailUiState(capabilityRow(s, group), group.description,
            if (Curriculum.available(s, next)) if (group.region != null) "进入${group.region.title}训练" else if (Curriculum.mastered(s, next.id)) "开始复习" else "开始 / 继续学习" else null,
            false, listOf(history),
            s.physicalReports.filter { it.lessonId == n.id }.takeLast(1).map { "上次实琴自评：${it.rating}" }, PhysicalPractice.exercises(n.id))
    }
    fun pilot(s: LearnerState): PilotMenuUi = PilotMenuUi(s.pilot != null || s.pausedTraining?.pilot != null,
        PilotMode.entries.associateWith { ShortScorePilot.nextClip(s) },
        PilotMode.entries.associateWith { mode -> ShortScorePilot.nextClip(s)?.let { ShortScorePilot.available(s,it) && ShortScorePilot.modeAvailable(s,it,mode) } == true },
        s.pilotResults.map { r -> "${r.mode.title} · ${if(r.kind==NotationKind.TAB) "TAB" else "五线谱"} · ${when(r.role){PilotRole.BASELINE->"基线";PilotRole.PRACTICE->"练习";PilotRole.RETEST->"复测"}} 第${r.clip+1}段 · ${r.elapsedMs/1000}秒 · " +
            (if(r.mode == PilotMode.GUITAR) "自评：${r.rating}" else "首次正确 ${r.firstCorrect}/${r.notes}") + (if(r.assisted) "（含辅助）" else "") +
            (if(r.comment.isBlank()) "" else "\n备注：${r.comment}") }, s.pilot?.mode ?: s.pausedTraining?.pilot?.mode)
    fun history(s: LearnerState) = s.sessions.asReversed().map { session ->
        val attempts = s.attempts.filter { it.sessionId == session.id }
        InfoPanelUi(formatTime(session.startedAt), (if (session.mode == "practice") "专项 · " else "学习 · ") + if (session.endedAt == null) "进行中 / 已暂停" else "已结束",
            listOf("完成${attempts.count { it.completed }}个任务 · 独立回答${attempts.count { it.independent } + attempts.sumOf { it.members.count { m -> m.independent } }}项"),
            attempts.map { it.task.nodeId }.distinct().map { ChoiceUi(it, Curriculum.node(it).title) })
    }
    fun settings(s: LearnerState, busy: Boolean, notice: String?) = SettingsUiState(AppTheme.fromId(s.themeId).id,
        FingeringMode.fromId(s.fingeringMode).id, s.soundEnabled, busy, notice, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    fun examples(s: LearnerState, id: String, busy: Boolean, audio: AudioUiState): ChordExamplesUiState {
        val shape = ChordShapes.get(id)
        val board = TrainingUiAdapter.board(ActiveTask(ChordLessons.make(shape, "chord-am", TaskSource.DEMONSTRATION)), FingeringMode.fromId(s.fingeringMode))
        return ChordExamplesUiState(ChordShapes.all.map { ChoiceUi(it.id, it.title) }, shape.title,
            board.copy(chordVertical = s.chordVertical, viewId = "example:$id", interaction = BoardInteraction.DISABLED, interactivePositions = emptySet()), s.fingeringMode, s.soundEnabled, busy, audio)
    }
    fun practice(s: LearnerState, scope: List<String>, selected: List<String>?, kindName: String?, busy: Boolean): PracticeUiState {
        val available = scope.map(Curriculum::node).filter { PracticeLessons.eligible(s, it) }
        val chosen = available.filter { selected == null || it.id in selected }
        val kinds = PracticeLessons.kinds(chosen, s)
        val kind = kinds.firstOrNull { it.name == kindName } ?: kinds.firstOrNull()
        val pausedPlan = s.practice ?: s.pausedTraining?.practice
        return PracticeUiState(available.map { ChoiceUi(it.id, it.title) }, chosen.map { it.id }, kinds.map { ChoiceUi(it.name, it.title) }, kind?.name,
            busy, !busy && chosen.isNotEmpty() && kind != null,
            if (pausedPlan?.nodeIds == chosen.map { it.id } && pausedPlan?.kind == kind) "继续专项" else "开始专项")
    }
    private fun attemptLabel(a: Attempt): String = when {
        a.task.source == TaskSource.PREVIEW -> "预学习接触，不计过关"
        a.task.source == TaskSource.DEMONSTRATION -> "跟随示范"
        a.firstCorrect == false -> if (a.corrected) "首次答错，已纠正" else "首次答错"
        a.hintLevel > 0 -> "提示辅助回答"
        a.independent -> "独立正确"
        else -> "练习正确，尚未满足证据间隔"
    }
    private fun formatTime(at: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
}
