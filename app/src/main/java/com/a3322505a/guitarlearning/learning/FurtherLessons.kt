package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.math.abs

/** Remaining K1–K5 goals; all old R7 skill identities and pass rules remain unchanged. */
object FurtherLessons {
    private fun node(id: String, title: String, description: String, vararg pre: String) = CurriculumNode(id,title,Category.ADVANCED,description,pre.toList())
    val nodes = listOf(
        node("accidentals","升、降与还原","F♯比F高半音；B♭比B低半音。相同音高不等于相同拼写。","structure"),
        node("octave-build","主动构建八度","从参照找高低八度，包括变化音。","pitch-relations","accidentals"),
        node("interval-build","从根音构建三度与五度","换根音与弦组，按实际音高构建目标。","intervals","accidentals"),
        node("ear-height","听高低与方向","先听参照，逐步比较更高、更低或同高。","structure"),
        node("pentatonic-a","A小调五声音阶","先两根弦局部，再连接A–C–D–E–G。","p03","structure"),
        node("pulse-basics","拍格与时值","4/4一小节四拍，认识四分、二分与休止。","tab01"),
        node("eighth-basics","八分细分与休止","先在一个已学音上分出半拍。","pulse-basics"),
        node("motif-answer","两至四音问答","用A小调五声音阶回应短动机，结尾回A。","pentatonic-a","pulse-basics"),
        node("major-retrieval","大调级数与随机起点","按指定级数、缺失音与主音检索C大调。","scale-major"),
        node("minor-tonic","相同音列，不同主音","比较C大调与A自然小调的三级及落点。","scale-minor"),
        node("power-move","移动强力和弦","换根音保持纯五度与八度结构。","power-structure"),
        node("triad-build","主动构建根、三、五","和弦名与组成音双向，多根音找音。","triads","accidentals"),
        node("triad-inversions","三和弦转位","最低实际音高决定转位，不看题面排列。","triad-build"),
        node("arpeggios","琶音与不同起音","从根、三、五音起步，成员和顺序分别记录。","triad-inversions"),
        node("position-connect","相邻区域连接","同音高换把，再从不同起点回根音。","pentatonic-a","middle"),
        node("voice-leading","共同音与邻近路线","C/F/G/Am之间保留共同音或找邻近音。","triad-build"),
        node("diatonic-chords","C大调调内和弦","叠置调内三度，认识I至vii°。","major-retrieval","triad-build"),
        node("progressions","短和弦进行","按级数选择C大调和弦进行。","diatonic-chords"),
        node("chord-landings","随和弦选择落点","根据当前和弦选旋律落点，允许多个答案。","progressions","voice-leading"),
        node("ear-transfer","多参照音程听辨","旋律式与同时发声分别留证据。","ear-intervals","ear-height"),
        node("ear-memory","三至五音旋律回忆","先重建音高顺序，再单独听辨时值；屏幕作答不计演奏能力。","ear-height","pentatonic-a","eighth-basics"),
        node("compose-eight","八小节小作品","C大调内两句四小节，先停G、最后回C。","motif-answer","major-retrieval","chord-landings","position-connect"),
        node("keys-g-f","G/F大调与调号","G大调用F♯，F大调用B♭，拼写按调性。","accidentals","major-retrieval"),
        node("transpose-pentatonic","移调与大调五声音阶","C/D/G大调五声音阶，换主音保持结构。","keys-g-f","pentatonic-a"),
        node("rework-key","换调重做自己的作品","将已完成C大调作品下移纯四度到G，保持节奏。","compose-eight","transpose-pentatonic"))
    val ids get() = nodes.map { it.id }
    val board = PhysicalRange(0,12)
    fun pitch(m: Int) = LessonExplanations.pitch(m)
    fun key(id: String, fact: String) = "extension:$id:$fact"
    fun coordinate(m: Int) = board.positions().first { MusicFacts.midi(it.string,it.fret)==m }
    fun choice(id: String,fact: String,prompt: String,explanation: String,answer: String,choices: List<String>,
        reference: List<Int> = emptyList(),targets: List<Int> = emptyList(),ear: Boolean=false,chord: Boolean=false) = LearningTask(
        nodeId=id,skillId=key(id,fact),direction=if(ear)Direction.REFERENCE_EAR else Direction.STRUCTURE,prompt=prompt,explanation=explanation,
        constraint=AnswerConstraint(ConstraintKind.SYMBOL,symbol=answer),options=choices,range=board,
        relation=if(reference.isEmpty())null else RelationPrompt(reference,targets.ifEmpty{reference},chord,ear))
    fun find(id: String,fact: String,reference: Int,target: Int,prompt: String,explanation: String,range: PhysicalRange=board) = LearningTask(
        nodeId=id,skillId=key(id,fact),direction=Direction.STRUCTURE,prompt=prompt,explanation=explanation,
        constraint=AnswerConstraint(ConstraintKind.PITCH,midi=target),range=range,
        referenceCoordinates=listOf(coordinate(reference)),relation=RelationPrompt(listOf(reference),listOf(target)))
    fun sequence(id: String,fact: String,pitches: List<Int>,prompt: String,explanation: String,reference: Int=pitches.first(),range: PhysicalRange=board,ear: Boolean=false): LearningTask {
        val rules=pitches.map{AnswerConstraint(ConstraintKind.PITCH,midi=it)}
        return LearningTask(nodeId=id,skillId=key(id,fact),direction=if(ear)Direction.REFERENCE_EAR else Direction.STRUCTURE,prompt=prompt,explanation=explanation,
            constraint=rules.first(),sequence=rules,completion=CompletionKind.SEQUENCE,targetSkillIds=pitches.indices.map{key(id,"$fact:member:$it")},
            range=range,relation=RelationPrompt(listOf(reference),pitches,ear=ear))
    }
    fun permitted(classes: Set<Int>,low: Int=45,high: Int=72) = AnswerConstraint(ConstraintKind.PITCH_SET,allowedPitches=(low..high).filter{it%12 in classes}.toSet())
    fun composition(id: String,fact: String,rules: List<AnswerConstraint>,durations: List<Int>,prompt: String,explanation: String,tonic: Int=0) = LearningTask(
        nodeId=id,skillId=key(id,fact),direction=Direction.STRUCTURE,prompt=prompt,explanation=explanation,constraint=rules.first(),sequence=rules,
        completion=CompletionKind.SEQUENCE,targetSkillIds=rules.indices.map{key(id,"$fact:member:$it")},range=board,creationDurations=durations,tonicPitchClass=tonic)

    fun tasks(id: String): List<LearningTask> = when(id) {
        "accidentals" -> listOf(
            choice(id,"sharp","F（1弦1品）升半音，怎样拼写？","F4→F♯4，到1弦2品；♯表示升半音。","F♯",listOf("F♯","F♭","F♮")),
            choice(id,"flat","B降低半音怎样拼写？","B→B♭。B♭与A♯同音高，这里是把B降低。","B♭",listOf("B♭","B♯","A♮")),
            choice(id,"natural","前面写F♯，恢复F本音用什么？","F♯→F♮：还原号取消升号，实际降低半音。","F♮",listOf("F♮","F♯","F♭")),
            choice(id,"enharmonic","F大调第四级写B♭还是A♯？","F–G–A–B♭–C–D–E，每字母一次，第四级写B♭。","B♭",listOf("B♭","A♯")),
            find(id,"sharp-position",65,66,"从F4升半音，找到F♯4","F4（1弦1品）→F♯4（1弦2品），升半音。"),
            find(id,"flat-position",59,58,"从B3降半音，找到B♭3","B3→B♭3降低半音；例如3弦3品。"))
        "octave-build" -> listOf(48,50,54,57,60).flatMap { root -> listOf(false,true).map { down ->
            val ref=if(down)root+12 else root;val target=if(down)root else root+12
            find(id,"$root:$down",ref,target,"从${pitch(ref)}找${if(down)"低" else "高"}八度","${pitch(ref)}→${pitch(target)}：${if(down)"降低" else "升高"}12个半音。同音名，不同实际音高。")
        } }
        "interval-build" -> listOf(48,53,57,60).flatMap { root -> listOf(3,4,7).map { distance ->
            find(id,"$root:$distance",root,root+distance,"从${pitch(root)}构建上方${MusicRelations.intervals[distance]}","${pitch(root)}→${pitch(root+distance)}：升${distance}个半音；范围内同音高位置均可。")
        } }
        "ear-height" -> listOf(12,7,2).flatMap { gap -> listOf(52,59,64).flatMap { root -> listOf(-1,0,1).map { sign ->
            val target=root+gap*sign;val answer=if(sign>0)"更高" else if(sign<0)"更低" else "同高"
            choice(id,"$gap:$root:$sign","听参照后，第二音更高、更低还是同高？","${pitch(root)}→${pitch(target)}，$answer；比较方向，不猜绝对音名。",answer,listOf("更高","更低","同高"),listOf(root),listOf(target),ear=true)
        } } }
        "pentatonic-a" -> listOf(
            sequence(id,"local",listOf(60,62,64),"先在1–2弦连接C–D–E","2弦1品C4→3品D4→1弦空弦E4。",57,PhysicalRange(0,3,setOf(1,2))),
            sequence(id,"root",listOf(64,67,69),"局部E–G–A，回主音A","1弦空弦E4→3品G4→5品A4。",57,PhysicalRange(0,5,setOf(1,2))),
            sequence(id,"up",listOf(57,60,62,64,67,69),"A小调五声音阶上行","A–C–D–E–G–A；省去自然小调B、F。",57,PhysicalRange(0,5,setOf(1,2,3))),
            sequence(id,"down",listOf(69,67,64,62,60,57),"从高A回低A","A–G–E–D–C–A；落回主音。",57,PhysicalRange(0,5,setOf(1,2,3))))
        "pulse-basics", "eighth-basics" -> rhythmTasks(id) + beatGrid(id)
        "motif-answer" -> (2..4).map { count ->
            val rules=List(count-1){permitted(setOf(9,0,2,4,7),57,69)}+permitted(setOf(9),57,69)
            composition(id,"$count",rules,if(count==2)listOf(8,8) else if(count==3)listOf(4,4,8) else List(4){4},
                "$count 音回应：五声音阶，最后回A","上方是问题谱A–C–休止–A；回应可选A/C/D/E/G，最后A。节奏${if(count==2)"二分、二分" else if(count==3)"四分、四分、二分" else "四个四分音符"}。多种旋律都可以。",9)
                .copy(referenceCoordinates=listOf(Coordinate(3,2),Coordinate(2,1)),referenceScore=questionScore())
        } + listOf(endingVariation())
        else -> FurtherHarmony.tasks(id)
    }

    private fun questionScore() = ShortScore("motif-question",bars=1,events=listOf(
        ScoreEvent(0,4,57,Coordinate(3,2)),ScoreEvent(4,4,60,Coordinate(2,1)),ScoreEvent(8,4),ScoreEvent(12,4,57,Coordinate(3,2))))
    private fun endingVariation(): LearningTask = composition("motif-answer","ending-only",
        listOf(AnswerConstraint(ConstraintKind.PITCH,midi=57),AnswerConstraint(ConstraintKind.PITCH,midi=60),permitted(setOf(9),57,69)),listOf(4,4,8),
        "只改变结尾：A3–C4–E4 改为回A", "前两音A3、C4及四分、四分、二分节奏不变；只把最后的E改为A。允许范围内任一A。",9)
        .copy(referenceScore=ShortScore("motif-ending-question",bars=1,events=listOf(ScoreEvent(0,4,57,Coordinate(3,2)),ScoreEvent(4,4,60,Coordinate(2,1)),ScoreEvent(8,8,64,Coordinate(1,0)))))
    private fun rhythmTasks(id: String): List<LearningTask> {
        val patterns=if(id=="pulse-basics")listOf(listOf(4,4,4,4),listOf(8,8),listOf(4,4,8)) else listOf(listOf(2,2,4,4,4),listOf(4,2,2,8),listOf(2,2,2,2,4,4))
        return patterns.flatMapIndexed { n,durations -> listOf(false,true).map { rest ->
            var tick=0
            val events=durations.mapIndexed { i,d -> ScoreEvent(tick,d,if(rest&&i==1)null else 64,if(rest&&i==1)null else Coordinate(1,0)).also{tick+=d} }
            val score=ShortScore("rhythm-$id-$n-$rest",events=events,bars=1)
            val answer="${(if(rest)durations[1] else durations.first())/4.0}拍"
            choice(id,"$n:$rest",if(rest)"谱中的休止持续几拍？" else "第一个音持续几拍？","四分一拍、二分两拍、八分半拍；休止占时值但不发声。目标为$answer。",answer,listOf("0.5拍","1.0拍","2.0拍"))
                .copy(notation=score.notation(NotationKind.TAB))
        } } + listOf(choice(id,"bar","4/4一小节有几个四分音符拍？","以四分音符为一拍，每小节四拍，休止也计入。","4拍",listOf("3拍","4拍","8拍")))
    }

    fun beatGrid(id: String): LearningTask {
        val eighth = id == "eighth-basics"
        val durations = if(eighth) listOf(2,2,4,4,2,2) else listOf(4,4,4,4)
        var tick = 0
        val score = ShortScore("grid-$id",bars=1,events=durations.mapIndexed { i,d ->
            ScoreEvent(tick,d,if(i==2 && !eighth || i==3 && eighth)null else 64,if(i==2 && !eighth || i==3 && eighth)null else Coordinate(1,0)).also { tick += d }
        })
        val actual = score.events.filter { it.midi != null }.map { e -> "${e.tick/4+1}" + if(e.tick%4==2) "&" else "" }
        val rules = actual.map { AnswerConstraint(ConstraintKind.SYMBOL,symbol=it) }
        return LearningTask(nodeId=id,skillId=key(id,"grid"),direction=Direction.STRUCTURE,
            prompt="按谱依次选出每个音的起拍位置",explanation="拍格1、2、3、4；&表示后半拍。休止占拍但不起音。本题起音：${actual.joinToString(" → ")}。",
            constraint=rules.first(),sequence=rules,completion=CompletionKind.SEQUENCE,targetSkillIds=rules.indices.map{key(id,"grid:$it")},
            options=if(eighth)listOf("1","1&","2","2&","3","3&","4","4&") else listOf("1","2","3","4"),notation=score.notation(NotationKind.TAB))
    }

    fun hasOwnWork(state: LearnerState) = state.attempts.any { it.task.nodeId == "compose-eight" && it.completed && it.task.creationDurations.size == 16 }
    fun adaptOwnWork(state: LearnerState, task: LearningTask): LearningTask {
        if(task.nodeId!="rework-key")return task
        val original=requireNotNull(state.attempts.lastOrNull{it.task.nodeId=="compose-eight"&&it.completed}) { "请先完成并保存自己的八小节作品。" }
        val pitches=original.task.sequence.indices.map { index ->
            original.inputs.first { it.targetIndex==index&&it.coordinate!=null&&it.result!=ClickResult.WRONG }.coordinate!!.let { MusicFacts.midi(it.string,it.fret)-5 }
        }
        return task.copy(sequence=pitches.map{AnswerConstraint(ConstraintKind.PITCH,midi=it)},constraint=AnswerConstraint(ConstraintKind.PITCH,midi=pitches.first()),
            relation=RelationPrompt(listOf(55),pitches),creationDurations=original.task.creationDurations,
            explanation="原作品每音下移5个半音：${pitches.joinToString(" → "){pitch(it)}}。时值不变，保持轮廓。")
    }
}
