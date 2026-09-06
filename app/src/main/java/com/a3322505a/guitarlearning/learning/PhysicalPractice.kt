package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable

@Serializable data class PhysicalReport(val lessonId: String, val exerciseId: String, val rating: String, val at: Long)
data class PhysicalExercise(val id: String, val title: String, val instruction: String)
object PhysicalPractice {
    fun exercises(node: String): List<PhysicalExercise> = when(node) {
        "p01","pentatonic-a" -> listOf(
            PhysicalExercise("t01","清晰单音","拿琴慢弹本课两个音，每音听清后再换。检查杂音和按弦，不追速度。"),
            PhysicalExercise("t02","交替拨弦与消音","用本课短句慢速下拨、上拨交替；空闲手指轻触不发声的弦，听是否有多余弦响。"))
        "position-connect","arpeggios" -> listOf(PhysicalExercise("t03","击弦、勾弦与滑音","先在同弦两个已学音之间分开练击弦、勾弦，再练一次滑音。保持两音清晰，感觉用力或不适就暂停。"))
        "keys-g-f","compose-eight","rework-key" -> listOf(PhysicalExercise("t04","推弦与揉弦","先弹目标音作参照，再从相邻低音小幅推至目标；揉弦围绕一个稳定音高慢练。按听感自评，不由屏幕作答推断手上掌握。"))
        else -> emptyList()
    }
}
