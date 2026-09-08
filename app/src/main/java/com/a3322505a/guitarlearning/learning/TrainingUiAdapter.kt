package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts

/** The only task-to-board adapter; hidden answer sets never cross the display boundary. */
object TrainingUiAdapter {
    fun chordVisible(a: ActiveTask) = BoardTeachingPolicy.chordVisible(a)
    fun board(a: ActiveTask, mode: FingeringMode, busy: Boolean = false, displayLastFret: Int = a.task.range.lastFret, introduced: Set<String> = emptySet()): FretboardUiState {
        val t = a.task
        val facts = BoardTeachingPolicy.facts(a, introduced, displayLastFret)
        val display = facts.display
        val marks = facts.positions.map { fact ->
            val c = fact.coordinate
            BoardMark(c,
                when { fact.wrong -> MarkRole.WRONG; fact.correct -> MarkRole.CORRECT; fact.reference -> MarkRole.REFERENCE; else -> MarkRole.TARGET },
                when (fact.knowledge) {
                    PositionKnowledge.MISTAKE -> "×"
                    PositionKnowledge.NOTE -> MusicFacts.note(c.string, c.fret)
                    PositionKnowledge.CONFIRMED -> "✓"
                    PositionKnowledge.QUESTION -> "?"
                    PositionKnowledge.GUIDED_SYMBOL -> t.constraint.symbol.orEmpty()
                    PositionKnowledge.NONE -> ""
                },
                fact.target && !fact.correct && !fact.wrong && t.constraint.kind in listOf(ConstraintKind.STRING, ConstraintKind.FRET))
        }
        val chord = t.chord?.let { shape ->
            val visible = facts.chordVisible
            val labels = (1..6).mapNotNull { string ->
                val fret = shape.fret(string)
                val confirmedMute = a.inputs.any { it.symbol == "X" && it.result != ClickResult.WRONG &&
                    it.targetIndex?.let { index -> t.sequence.getOrNull(index)?.string } == string }
                if ((visible || confirmedMute || Coordinate(string, 0) in a.confirmed) && (fret == null || fret == 0)) string to if (fret == null) "X" else "O" else null
            }.toMap()
            ChordOverlayUiState(
                if (visible) shape.fingers.map { FingerUiSpan(it.finger, it.fret, it.firstString, it.lastString) } else emptyList(),
                facts.chordPositions.map { c -> ChordToneUi(c,
                    when (mode) { FingeringMode.COLORS -> ""; FingeringMode.NUMBERS -> shape.fingerAt(c)?.toString().orEmpty(); FingeringMode.NOTES -> MusicFacts.note(c.string, c.fret) },
                    mode == FingeringMode.NOTES && MusicFacts.midi(c.string, c.fret) % 12 == shape.root, mode == FingeringMode.COLORS) }, labels)
        }
        val teaching = t.nodeId == "g00" && t.source == TaskSource.DEMONSTRATION && a.phase == Phase.ANSWERING
        val input = BoardTeachingPolicy.input(a, busy, facts)
        val interaction = when (input.mode) {
            PositionInputMode.DISABLED -> BoardInteraction.DISABLED
            PositionInputMode.AUDITION -> BoardInteraction.AUDITION
            PositionInputMode.ANSWER -> BoardInteraction.ANSWER
        }
        return FretboardUiState(t.id, display.firstFret, display.lastFret, input.interactivePositions, interaction, marks, chord,
            t.constraint.string?.takeIf { teaching && !t.hideStringLabels }?.let { "第${it}弦" },
            t.constraint.fret?.takeIf { teaching && !t.hideFretLabels && t.constraint.string == null }?.let { it to if (it == 0) "空弦" else "${it}品" }, answerPositions = input.answerPositions, chordTitle = t.chord?.title.orEmpty()).let { b ->
                if (t.chord == null) b else b.copy(firstFret = t.range.firstFret, lastFret = t.range.lastFret)
            }
    }

    fun training(s: LearnerState, busy: Boolean, audio: AudioUiState): TrainingUiState {
        val a = s.active ?: return TrainingUiState(summary = s.endedSummary ?: "进度已保存。", busy = busy)
        val t = a.task
        val answerable = !busy && a.phase in listOf(Phase.ANSWERING, Phase.CORRECTING) && (t.relation?.ear != true || a.audioReady && !audio.playing)
        val mistake = a.inputs.lastOrNull { it.result == ClickResult.WRONG }?.symbol
        val optionAnswer = if (t.completion == CompletionKind.SEQUENCE) t.sequence.getOrNull(a.sequenceIndex)?.symbol else t.constraint.symbol
        val options = t.options.map { option ->
            val confirmed = a.phase in listOf(Phase.CORRECT, Phase.CORRECTED) && option == optionAnswer
            val shown = (t.guided || a.hintLevel >= 2 || a.phase == Phase.CORRECTING || confirmed) && option == optionAnswer
            AnswerOptionUi(option, when { confirmed -> MarkRole.CORRECT; mistake == option -> MarkRole.WRONG; shown -> MarkRole.TARGET; else -> null }, answerable)
        }
        val hasBoard = t.referenceCoordinates.isNotEmpty() || t.chord != null || t.constraint.kind != ConstraintKind.SYMBOL || t.coordinate != null
        val relation = t.relation?.let { r ->
            val lines = mutableListOf("参考音 ${r.referencePitches.joinToString(" / ") { pitchLabel(it) }}")
            if (t.completion == CompletionKind.SEQUENCE) lines += if (chordVisible(a)) r.targetPitches.mapIndexed { i, p -> (if (i == a.sequenceIndex) "▸" else "") + (r.targetSpellings.getOrNull(i) ?: pitchLabel(p)) }.joinToString("  ")
                else "第${(a.sequenceIndex + 1).coerceAtMost(t.sequence.size)} / ${t.sequence.size}项 · 点击范围内正确音高"

            RelationUiState(lines, if (r.ear) null else "试听示范", !busy && !audio.playing && s.soundEnabled)
        }
        val extraRelation = if (t.creationDurations.isNotEmpty() || relation == null && (t.chordProgression.isNotEmpty() || t.notation?.score != null)) {
            val lines = if (t.creationDurations.isNotEmpty()) listOf("第${(a.sequenceIndex+1).coerceAtMost(t.sequence.size)}/${t.sequence.size}音 · 本音${t.creationDurations.getOrNull(a.sequenceIndex)?.div(4.0) ?: 0.0}拍") else emptyList()
            val displayLines = (if(t.referenceScore != null) listOf("上方是问题谱；按条件创作回应。") else emptyList()) + lines
            RelationUiState(displayLines,if(t.creationDurations.isNotEmpty())"听本次短句" else "试听示范", !busy && s.soundEnabled && !audio.playing &&
                (t.creationDurations.isEmpty() || a.phase in listOf(Phase.CORRECT,Phase.CORRECTED)))
        } else relation
        val rule = t.sequence.getOrNull(a.sequenceIndex)
        val string = rule?.coordinate?.string ?: rule?.string
        val controls = if (t.chord != null && string != null) ChordControlsUiState(string, "${a.sequenceIndex + 1}/${t.sequence.size}", answerable, chordVisible(a) && s.soundEnabled && !busy) else null
        val message = when { a.phase == Phase.CORRECTED -> "已纠正。"; a.phase == Phase.CORRECT -> null; a.phase == Phase.CORRECTING -> CorrectionPresentation.message(a, s.introductions); a.feedback.isNotBlank() -> a.feedback; t.guided -> t.explanation; else -> s.familyRuns[t.adaptive?.familyScope]?.run?.reason }
        val plainRecognition = t.direction == Direction.POSITION_TO_NOTE && !t.guided && t.tonicPitchClass == null
        return TrainingUiState(t.id, if (plainRecognition) "" else t.prompt, busy = busy,
            board = if (hasBoard && s.pilot?.mode != PilotMode.GUITAR) board(a, FingeringMode.fromId(s.fingeringMode), busy, displayLast(s), s.introductions).copy(chordVertical = s.chordVertical) else null,
            tab = t.coordinate.takeIf { t.showTab }, notation = t.notation ?: t.referenceScore?.notation(NotationKind.TAB), notationIndex = if(t.referenceScore != null) -1 else a.sequenceIndex,
            message = message?.let(::fretboardInstruction), wrong = a.firstCorrect == false, options = options, relation = extraRelation, chordControls = controls,
            showLegend = t.chord != null && !s.fingerLegendSeen, hasChord = t.chord != null,
            canHint = s.pilot == null && a.phase == Phase.ANSWERING && !t.guided && !busy, hintLabel = if (a.hintLevel == 0) "提示" else "看示范",
            canNext = (a.phase == Phase.CORRECTED || t.creationDurations.isNotEmpty() && a.phase == Phase.CORRECT) && !busy,
            autoNextDelayMs = if (s.pilot == null && t.creationDurations.isEmpty() && (a.phase == Phase.CORRECT || a.phase == Phase.CORRECTED && s.regionTraining != null && RegionRounds.finished(s)) && !busy) if (t.guided) 1200L else 650L else null,
            soundEnabled = s.soundEnabled, canReplay = TaskAudioPolicy.prompt(a) != null && s.soundEnabled && !(t.relation?.ear == true && (audio.playing || busy)), audio = audio,
            roundProgress = s.regionTraining?.let { "${RegionRounds.issued(s).size.coerceAtMost(12)}/12" },
            accessibilityPrompt = if (t.adaptive?.options?.isNotEmpty() == true) (if (t.tonicPitchClass != null) "${t.prompt}：" else "") + "选择亮起位置对应的音，选项可能使用音名、固定唱名或调内级数" else t.prompt)
    }
    fun displayLast(s: LearnerState): Int = BoardTeachingPolicy.displayLast(s)
    private fun pitchLabel(midi: Int) = "${MusicFacts.noteNames[midi % 12]}${midi / 12 - 1}"
}

internal fun fretboardInstruction(text: String): String = text
    .replace("先看弦号，再找品格。", "先凭粗细找到琴弦，再从弦枕和圆点辨认品格。")
    .replace("先看弦号和所在区域", "先看琴弦粗细、弦枕和定位圆点")
    .replace("左侧单独的一格用来表示空弦。", "点弦枕左侧的琴弦表示弹空弦。")
    .replace("空弦不按品，用最左侧0区表示。", "空弦不按品，点弦枕左侧的琴弦。")
