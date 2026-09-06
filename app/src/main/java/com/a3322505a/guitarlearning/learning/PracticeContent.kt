package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PracticeContent(state: LearnerState, scope: List<String>, busy: Boolean, start: (PracticePlan) -> Unit) {
    val available = scope.map(Curriculum::node).filter { PracticeLessons.eligible(state, it) }
    var selected by rememberSaveable(scope) { mutableStateOf(available.map { it.id }) }
    val chosen = available.filter { it.id in selected }
    val kinds = PracticeLessons.kinds(chosen, state)
    var kindName by rememberSaveable(scope) { mutableStateOf(kinds.firstOrNull()?.name) }
    val kind = kinds.firstOrNull { it.name == kindName } ?: kinds.firstOrNull()
    Text("选择已接触的内容", fontSize = 19.sp)
    Text("专项不会推进课程顺序。独立回答按对应技能记录；提示和示范不算独立掌握。", fontSize = 13.sp)
    if (available.isEmpty()) Text("先在学习中接触该范围，再来专项练习。")
    available.forEach { node ->
        Row(Modifier.fillMaxWidth().toggleable(value = node.id in selected, enabled = !busy, role = Role.Checkbox) {
            selected = if (it) selected + node.id else selected - node.id
        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = node.id in selected, onCheckedChange = null)
            Text(node.title, Modifier.weight(1f))
        }
    }
    kinds.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = item == kind, onClick = { kindName = item.name }, enabled = !busy)
            Text(item.title)
        }
    }
    Button(onClick = { kind?.let { start(PracticePlan(chosen.map { it.id }, it)) } }, enabled = !busy && chosen.isNotEmpty() && kind != null) {
        Text(if (state.practice?.nodeIds == chosen.map { it.id } && state.practice?.kind == kind) "继续专项" else "开始专项")
    }
    Text("返回可暂停，首页可继续；结束专项后恢复原来的学习任务。", fontSize = 13.sp)
}
