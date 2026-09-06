package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import com.a3322505a.guitarlearning.learning.FurtherLessons.board
import com.a3322505a.guitarlearning.learning.FurtherLessons.choice
import com.a3322505a.guitarlearning.learning.FurtherLessons.composition
import com.a3322505a.guitarlearning.learning.FurtherLessons.coordinate
import com.a3322505a.guitarlearning.learning.FurtherLessons.find
import com.a3322505a.guitarlearning.learning.FurtherLessons.key
import com.a3322505a.guitarlearning.learning.FurtherLessons.permitted
import com.a3322505a.guitarlearning.learning.FurtherLessons.pitch
import com.a3322505a.guitarlearning.learning.FurtherLessons.sequence
import kotlin.math.abs

internal object FurtherHarmony {
    val roman=listOf("I","ii","iii","IV","V","vi","vii°")
    val names=listOf("C","Dm","Em","F","G","Am","Bdim")
    val tones=listOf(listOf(48,52,55),listOf(50,53,57),listOf(52,55,59),listOf(53,57,60),listOf(55,59,62),listOf(57,60,64),listOf(59,62,65))
    fun tasks(id: String): List<LearningTask> = when(id) {
        "major-retrieval" -> (1..7).map { degree -> find(id,"degree:$degree",60,60+MusicRelations.major[degree-1],"C大调：找到第${degree}级","C4为1级，C–D–E–F–G–A–B；第${degree}级为${pitch(60+MusicRelations.major[degree-1])}。") } +
            listOf(choice(id,"missing","C–D–E–□–G，缺少哪个音？","第四级F；E→F半音，F→G全音。","F",listOf("F","F♯","E♭"))) +
            listOf(2,4,6).map { start -> sequence(id,"start:$start",MusicRelations.major.take(7).drop(start).map{60+it}+72,"从第${start+1}级上行回C","不从C开头，也保持C大调级数顺序。",60) }
        "minor-tonic" -> listOf(
            choice(id,"tonic","C大调与A自然小调主音相同吗？","C大调主音C，A自然小调主音A；音集合相同，中心不同。","不同",listOf("相同","不同")),
            choice(id,"third-major","C大调第三级？","C–D–E，E是第三级；C→E大三度。","E",listOf("E","C","F")),
            choice(id,"third-minor","A自然小调第三级？","A–B–C，C是第三级；A→C小三度。","C",listOf("C","C♯","E")),
            sequence(id,"major-end",listOf(64,62,60),"C大调3–2–1结束","E–D–C，落到主音C。",60),
            sequence(id,"minor-end",listOf(60,59,57),"A自然小调3–2–1结束","C–B–A，落到主音A。",57))
        "power-move" -> listOf(43,45,47,52).flatMap { root -> listOf(2,3).map { count -> sequence(id,"$root:$count",listOf(root,root+7,root+12).take(count),
            "以${pitch(root)}构建${count}音强力和弦","根音→纯五度${if(count==3)"→根音八度" else ""}；移动后保持0、7、12半音关系。",root) } }
        "triad-build" -> listOf(48,53,55,57).flatMap { root -> listOf(false,true).flatMap { minor ->
            val offsets=if(minor)listOf(0,3,7) else listOf(0,4,7);val pitches=offsets.map{root+it};val spelled=offsets.mapIndexed{i,o->SpelledPitch.fromNaturalRoot(root,i*2,o).label}
            val name=MusicFacts.noteNames[root%12]+if(minor)"m" else ""
            listOf(sequence(id,"$name:build",pitches,"构建$name：根、三、五","${spelled.joinToString("–")}；根音${pitch(root)}，各音距根音${offsets.joinToString("、")}个半音。",root).copy(relation=RelationPrompt(listOf(root),pitches,targetSpellings=spelled)),
                choice(id,"$name:name","${spelled.joinToString("–")}组成什么和弦？","根音${pitch(root)}，${if(minor)"小" else "大"}三度与纯五度组成$name。",name,listOf(name,MusicFacts.noteNames[root%12]+if(minor)"" else "m","G5")))
        } }
        "triad-inversions" -> listOf(48,53,57).flatMap { root -> (0..2).map { inversion ->
            val third=if(root==57)3 else 4
            val pitches=when(inversion){0->listOf(root+7,root,root+third);1->listOf(root+12,root+7,root+third);else->listOf(root+12,root+third+12,root+7)}
            val answer=listOf("原位","第一转位","第二转位")[inversion]
            choice(id,"$root:$inversion","${pitches.joinToString(" / "){pitch(it)}}：按最低实际音判断转位","最低音${pitch(pitches.min())}是${listOf("根音","三音","五音")[inversion]}，所以是$answer；题面排列不是低音次序。",answer,listOf("原位","第一转位","第二转位"),listOf(root),pitches,chord=true)
        } }
        "arpeggios" -> listOf(48,53,57).flatMap { root -> (0..2).map { start ->
            val notes=listOf(root,root+(if(root==57)3 else 4),root+7);val order=notes.drop(start)+notes.take(start)
            sequence(id,"$root:$start",order,"从${listOf("根音","三音","五音")[start]}开始琶音","顺序${order.joinToString(" → "){pitch(it)}}；成员与起音顺序分别记录。",root)
        } }
        "position-connect" -> listOf(60,62,64,67).map { target ->
            sequence(id,"$target",listOf(target,target,57),"同音高换到中区，再回A3","先在0–4品找${pitch(target)}，再到5–8品找同音高，最后回A3。",57)
                .let { t -> t.copy(sequence=t.sequence.mapIndexed { i,r -> r.copy(firstFret=if(i==1)5 else 0,lastFret=if(i==1)8 else 4) }) }
        }
        "voice-leading" -> voiceLeading(id)
        "diatonic-chords" -> names.indices.flatMap { degree ->
            val quality=if(degree==6)"减三和弦" else if(degree in listOf(1,2,5))"小三和弦" else "大三和弦"
            listOf(choice(id,"$degree:name","C大调${roman[degree]}级和弦？","从第${degree+1}级隔一个调内音叠置：${tones[degree].joinToString("–"){pitch(it)}} → ${names[degree]}。",names[degree],names),
                choice(id,"$degree:quality","C大调${roman[degree]}级和弦性质？","${names[degree]}是$quality；调内顺序：大、小、小、大、大、小、减。",quality,listOf("大三和弦","小三和弦","减三和弦")))
        }
        "progressions" -> listOf(listOf(0,3,4,0),listOf(0,5,3,4),listOf(1,4,0)).mapIndexed { n,degrees ->
            val rules=degrees.map{AnswerConstraint(ConstraintKind.SYMBOL,symbol=names[it])}
            LearningTask(nodeId=id,skillId=key(id,"$n"),direction=Direction.STRUCTURE,prompt="C大调：${degrees.joinToString("–"){roman[it]}}，依次选和弦",
                explanation=degrees.joinToString(" → "){"${roman[it]} = ${names[it]}"},constraint=rules.first(),sequence=rules,completion=CompletionKind.SEQUENCE,
                targetSkillIds=degrees.indices.map{key(id,"$n:chord:$it")},options=names,chordProgression=degrees.map{tones[it]})
        }
        "chord-landings" -> listOf(0,3,4,5).map { degree ->
            val classes=tones[degree].map{it%12}.toSet()
            LearningTask(nodeId=id,skillId=key(id,"$degree"),direction=Direction.STRUCTURE,prompt="当前和弦${names[degree]}：选一个和弦音作落点",
                explanation="可选${classes.joinToString("、"){MusicFacts.noteNames[it]}}；当前和弦根音与全曲主音不是同一个概念。",constraint=permitted(classes),range=board,referenceCoordinates=tones[degree].map{coordinate(it)})
        }
        "ear-transfer" -> listOf(50,55,62).flatMap { root -> listOf(3,4,7).flatMap { gap -> listOf(false,true).map { chord ->
            choice(id,"$root:$gap:$chord",if(chord)"参照后，同时发声的两个音相隔什么？" else "参照后，依次发声的两个音相隔什么？",
                "${pitch(root)}→${pitch(root+gap)}，相差${gap}个半音，${MusicRelations.intervals[gap]}。",MusicRelations.intervals[gap],listOf("小三度","大三度","纯五度"),
                listOf(root),if(chord)listOf(root,root+gap) else listOf(root+gap),ear=true,chord=chord)
        } } }
        "ear-memory" -> listOf(listOf(57,60,62),listOf(60,57,64),listOf(57,62,60,57),listOf(64,62,60,62),listOf(57,60,64,62,57),listOf(62,60,57,60,64)).mapIndexed { i,route ->
            sequence(id,"$i",route,"听完${route.size}音短句，再依次找到实际音高","参照A3；短句${route.joinToString(" → "){pitch(it)}}。只判断音高顺序，不判断演奏节奏。",57,ear=true)
        } + rhythmMemory(id)
        "compose-eight" -> listOf(composition(id,"work",List(16){i->permitted(if(i==7)setOf(7) else if(i==15)setOf(0) else setOf(0,2,4,5,7,9,11)).copy(firstFret=if(i<8)0 else 5,lastFret=if(i<8)4 else 8)},List(16){8},
            "写8小节：C大调，第4小节停G，第8小节回C","每小节两个二分音符，共16音；前4小节0–4品，后4小节5–8品，一次换把。用C大调自然音，第8音为G，第16音为C。核对这些条件，不设唯一旋律答案。"))
        "keys-g-f" -> listOf(
            choice(id,"g-signature","G大调调号改变哪个音？","G–A–B–C–D–E–F♯–G，第七级F♯保持全全半全全全半。","F♯",listOf("F♯","F","G♭")),
            choice(id,"f-signature","F大调第四级怎样拼写？","F–G–A–B♭–C–D–E–F，第四级B♭，不是A♯。","B♭",listOf("B♭","A♯","B")),
            sequence(id,"g-scale",MusicRelations.major.map{55+it},"从G3构建G大调","G–A–B–C–D–E–F♯–G，F♯为第七级。",55),
            sequence(id,"f-scale",MusicRelations.major.map{53+it},"从F3构建F大调","F–G–A–B♭–C–D–E–F，B♭为第四级。",53).copy(relation=RelationPrompt(listOf(53),MusicRelations.major.map{53+it},targetSpellings=MusicRelations.major.mapIndexed{i,o->SpelledPitch.fromNaturalRoot(53,i,o).label}))) +
            listOf(55,53).flatMap{root->(1..7).map{degree->find(id,"$root:$degree",root,root+MusicRelations.major[degree-1],"${if(root==55)"G" else "F"}大调：找到${degree}级","从本调主音按全全半全全全半构建，不能沿用C大调级数。")}}
        "transpose-pentatonic" -> listOf(48,50,55).flatMap { root ->
            val route=listOf(0,2,4,7,9,12).map{root+it}
            listOf(sequence(id,"$root:up",route,"${MusicFacts.noteNames[root%12]}大调五声音阶1–2–3–5–6–1","距主音0、2、4、7、9、12半音；换主音，结构不变。",root),
                sequence(id,"$root:down",route.reversed(),"从高主音下行回${pitch(root)}","同一音集合，顺序反向。",root))
        }
        "rework-key" -> listOf(sequence(id,"own-work",List(16){if(it==7)62 else 55},"将自己的C大调作品移到G大调","每音下移5个半音，节奏保持；新主音G。",55))
        else -> emptyList()
    }
    private fun rhythmMemory(id: String): List<LearningTask> = listOf(listOf(4,4,8),listOf(8,4,4),listOf(2,2,4,8)).mapIndexed { i,durations ->
        val pitches = if(i==2)listOf(57,60,62,57) else listOf(57,60,57)
        var tick=0
        val score=ShortScore("ear-rhythm-$i",bars=1,events=durations.mapIndexed { j,d ->
            ScoreEvent(tick,d,pitches[j],coordinate(pitches[j])).also{tick+=d}
        })
        val names=mapOf(2 to "八分",4 to "四分",8 to "二分")
        val answer=durations.joinToString("–"){names.getValue(it)}
        choice(id,"rhythm:$i","听完短句，选择依次听到的时值","先听参照A3，再听一小节。音高${pitches.joinToString("–"){pitch(it)}}；时值$answer。只记录节奏辨认。",answer,
            listOf("四分–四分–二分","二分–四分–四分","八分–八分–四分–二分"),listOf(57),pitches,ear=true).copy(auditoryScore=score)
    }
    private fun voiceLeading(id: String): List<LearningTask> = listOf(0 to 3,3 to 4,4 to 5,5 to 0).flatMap { (a,b) ->
        val classes=tones[b].map{it%12}.toSet();val common=tones[a].map{it%12}.toSet().intersect(classes)
        val prior=tones[a][1]+12;val candidates=(48..76).filter{it%12 in classes};val distance=candidates.minOf{abs(it-prior)}
        val rule=AnswerConstraint(ConstraintKind.PITCH_SET,allowedPitches=candidates.filter{abs(it-prior)==distance}.toSet())
        listOf(LearningTask(nodeId=id,skillId=key(id,"$a:$b:near"),direction=Direction.STRUCTURE,prompt="${names[a]}→${names[b]}：从${pitch(prior)}找最近的下一和弦音",
            explanation="按实际音高找最近的${names[b]}和弦音；同音高不同位置均可。最近相差${distance}个半音。",constraint=rule,range=board,referenceCoordinates=listOf(coordinate(prior)))) +
            if(common.isEmpty())listOf(choice(id,"$a:$b:common","${names[a]}与${names[b]}有共同音吗？","比较音集合，没有共同音。","没有",listOf("有","没有")))
            else listOf(LearningTask(nodeId=id,skillId=key(id,"$a:$b:common"),direction=Direction.STRUCTURE,prompt="${names[a]}→${names[b]}：保留一个共同音",
                explanation="可选${common.joinToString("、"){MusicFacts.noteNames[it]}}；保留音高可以不移动。",constraint=permitted(common),range=board))
    }
}
