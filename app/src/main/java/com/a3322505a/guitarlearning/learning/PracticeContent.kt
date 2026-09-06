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
fun PracticeContent(state: PracticeUiState, select: (String, Boolean) -> Unit, selectKind: (String) -> Unit, start: () -> Unit) {
    Text("选择已接触的内容", fontSize = 19.sp)
    Text("专项不会推进课程顺序。独立回答按对应技能记录；提示和示范不算独立掌握。", fontSize = 13.sp)
    if (state.nodes.isEmpty()) Text("先在学习中接触该范围，再来专项练习。")
    state.nodes.forEach { node ->
        Row(Modifier.fillMaxWidth().toggleable(value = node.id in state.selected, enabled = !state.busy, role = Role.Checkbox) {
            select(node.id, it)
        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = node.id in state.selected, onCheckedChange = null)
            Text(node.title, Modifier.weight(1f))
        }
    }
    state.kinds.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = item.id == state.kind, onClick = { selectKind(item.id) }, enabled = !state.busy)
            Text(item.title)
        }
    }
    Button(onClick = start, enabled = state.canStart) { Text(state.startLabel) }
    Text("返回可暂停，首页可继续；结束专项后恢复原来的学习任务。", fontSize = 13.sp)
}
