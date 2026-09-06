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
            n.prerequisites.joinToString("、") { Curriculum.node(it).title })
    }
    fun home(s: LearnerState): List<HomeEntryUi> {
        val current = if (s.sessionId != null) Curriculum.node(s.currentNode) else Curriculum.next(s)
        return HomeGroup.entries.map { group ->
            val active = current?.takeIf { it.category in group.categories }
            HomeEntryUi(group.name, group.title, active?.let { "当前：${it.title}" } ?: group.description,
                if (s.regionTraining != null) "继续训练" else if (s.practice != null) "继续专项" else if (s.sessionId != null) "继续学习" else "开始学习", active?.id, s.sessionId != null)
        } + HomeEntryUi("tree", "知识树", "${s.progress.count { it.value.masteredAt != null }} 个节点已点亮")
    }
    fun catalog(s: LearnerState, categories: Set<Category>, examples: Boolean = false) = CatalogUiState(categories.map { category ->
        if (category == Category.FRETBOARD) CatalogSectionUi(null, regions = FretboardRegion.entries.map { region ->
            RegionUi(region.name, "${region.title} · ${region.rangeLabel}", region.progressLabel(s), emptyList(), emptyList(),
                if (RegionTraining.available(s, region.name)) null else "完成前置内容后开始",
                if (RegionTraining.available(s, region.name)) if (s.active != null && (s.regionTraining?.regionId == region.name || RegionTraining.owner(s.currentNode) == region)) "继续" else "开始" else null)
        }) else CatalogSectionUi(category.title.takeIf { categories.size > 1 }, Curriculum.nodes.filter { it.category == category }.map { row(s, it) })
    }, examples)
    fun tree(s: LearnerState) = Curriculum.nodes.map { row(s, it) }
    fun node(s: LearnerState, n: CurriculumNode): NodeDetailUiState {
        val panels = mutableListOf<InfoPanelUi>()
        ChordLessons.shapes(n.id).forEach { shape -> panels += InfoPanelUi("${shape.title} · 逐弦证据", "每弦最近三次独立回答均正确；开放/不弹同样逐项记录。",
            listOf((6 downTo 1).joinToString(" · ") { string -> "${string}弦 ${MemberEvidencePolicy.evidence(s, ChordLessons.skill(shape, string)).takeLast(3).count { it.firstCorrect }}/3" })) }
        if (n.id == "mapping") panels += InfoPanelUi("映射证据", "各方向分别记录；固定唱名与 C 大调级数分别过关。",
            MappingLessons.notes.map { note -> "$note · 唱名 ${MappingLessons.evidence(s, note, false).takeLast(6).count { it.firstCorrect == true }}/6 · 级数 ${MappingLessons.evidence(s, note, true).takeLast(6).count { it.firstCorrect == true }}/6" } +
                MappingLessons.directions.map { direction -> "${MappingLessons.directionLabel(direction)}：独立正确 ${s.attempts.count { it.independent && it.firstCorrect == true && it.task.direction == direction }} 次" })
        if (n.id in ReadingLessons.ids || n.id in StructureLessons.ids) {
            val keys = if (n.id in ReadingLessons.ids) ReadingLessons.skills(n.id) else StructureLessons.keys(n.id)
            panels += InfoPanelUi("掌握依据", "各项目最近三次有效独立回答均正确；提示和示范不计入。",
                listOf("已达标 ${keys.count { StructureLessons.keyPassed(s, it) }} / ${keys.size} 项；顺序成员分别记录。") +
                    if (n.id.startsWith("ear-")) listOf("仅完整播放后接受作答；回放不透露选项答案，提示仍按辅助记录。") else emptyList())
        }
        n.positions.forEach { c ->
            val recent = MasteryPolicy.positionEvidence(s, c).takeLast(6)
            panels += InfoPanelUi("${c.label} · ${MusicFacts.label(c.string, c.fret)}", lines = listOf(
                "有效独立回答 ${recent.size}/6，最近正确 ${recent.count { it.firstCorrect == true }}/${recent.size}",
                "找位置 ${recent.count { it.task.direction == Direction.NOTE_TO_POSITION }} 次 · 看位置认音 ${recent.count { it.task.direction == Direction.POSITION_TO_NOTE }} 次",
                if (MasteryPolicy.positionPassed(s, c)) "该音位达到初步掌握条件" else "需要两个方向都独立作答；示范、提示和预学习不用于过关。"))
        }
        val attempts = s.attempts.filter { it.task.nodeId == n.id }
        val records = mutableListOf("记录 ${attempts.size} 次 · 提示 ${attempts.count { it.hintLevel > 0 }} 次 · 预学习 ${attempts.count { it.task.source == TaskSource.PREVIEW }} 次")
        if (attempts.any { it.members.isNotEmpty() }) records += "已回答成员 ${attempts.sumOf { it.members.size }} 项 · 独立正确 ${attempts.sumOf { it.members.count { m -> m.independent && m.firstCorrect } }} 项"
        s.progress[n.id]?.retainedOn?.let { records += "${it}有隔日独立正确记录。" }
        records += attempts.takeLast(6).asReversed().map { "${formatTime(it.at)} · ${it.task.prompt}\n${attemptLabel(it)}" }
        return NodeDetailUiState(row(s, n), n.description,
            if (Curriculum.available(s, n)) if (RegionTraining.owner(n.id) != null) "进入${RegionTraining.owner(n.id)!!.title}训练" else if (Curriculum.mastered(s, n.id)) "开始复习" else "开始 / 继续学习" else null,
            RegionTraining.owner(n.id) == null && PracticeLessons.eligible(s, n), panels, records)
    }
    fun pilot(s: LearnerState): PilotMenuUi = PilotMenuUi(s.pilot != null,
        PilotMode.entries.associateWith { ShortScorePilot.nextClip(s,it) },
        PilotMode.entries.associateWith { mode -> ShortScorePilot.nextClip(s,mode)?.let { ShortScorePilot.available(s,it) } == true },
        s.pilotResults.map { r -> "${r.mode.title} · ${r.kind} · 第${r.clip+1}段 · ${r.elapsedMs/1000}秒 · " +
            if(r.mode == PilotMode.GUITAR) "自评：${r.rating}" else "首次正确 ${r.firstCorrect}/${r.notes}" + if(r.assisted) "（含辅助）" else "" })
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
            board.copy(viewId = "example:$id", interaction = BoardInteraction.DISABLED, interactivePositions = emptySet()), s.fingeringMode, s.soundEnabled, busy, audio)
    }
    fun practice(s: LearnerState, scope: List<String>, selected: List<String>?, kindName: String?, busy: Boolean): PracticeUiState {
        val available = scope.map(Curriculum::node).filter { PracticeLessons.eligible(s, it) }
        val chosen = available.filter { selected == null || it.id in selected }
        val kinds = PracticeLessons.kinds(chosen, s)
        val kind = kinds.firstOrNull { it.name == kindName } ?: kinds.firstOrNull()
        return PracticeUiState(available.map { ChoiceUi(it.id, it.title) }, chosen.map { it.id }, kinds.map { ChoiceUi(it.name, it.title) }, kind?.name,
            busy, !busy && chosen.isNotEmpty() && kind != null,
            if (s.practice?.nodeIds == chosen.map { it.id } && s.practice?.kind == kind) "继续专项" else "开始专项")
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
