package com.a3322505a.guitarlearning.training

data class CurriculumIntroduction(
    val id: String,
    val text: String,
)

/** Short, one-shot explanations shown where theory first becomes useful. */
object CurriculumIntroductions {
    fun forQuestion(question: Question): CurriculumIntroduction? = when {
        question.curriculumLevel == 3 && question.kind == "half_step" ->
            CurriculumIntroduction("half_step", "相邻 1 品 = 半音")
        question.curriculumLevel == 3 && question.kind == "whole_step" ->
            CurriculumIntroduction("whole_step", "相隔 2 品 = 全音")
        question.curriculumLevel == 4 -> {
            val semitones = mapOf(
                "minor_second" to 1,
                "major_second" to 2,
                "minor_third" to 3,
                "major_third" to 4,
                "perfect_fourth" to 5,
                "perfect_fifth" to 7,
            )[question.kind]
            semitones?.let {
                CurriculumIntroduction(
                    question.kind,
                    question.prompt.substringAfter("向上") + " = $it 个半音",
                )
            }
        }
        question.kind.startsWith("c_major_scale_") ->
            CurriculumIntroduction("c_major_scale", "C 大调音阶：全全半全全全半")
        question.curriculumLevel == 5 ->
            CurriculumIntroduction("c_major_degrees", "C 大调：C=1 D=2 E=3 F=4 G=5 A=6 B=7")
        question.curriculumLevel == 6 ->
            CurriculumIntroduction("one_three_five", "1–3–5 = C–E–G")
        else -> null
    }
}
