package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RelationContent(state: RelationUiState, onDemonstrate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { state.lines.forEach { Text(it, fontSize = 14.sp) } }
        state.demonstrationLabel?.let { label ->
            OutlinedButton(onClick = onDemonstrate, enabled = state.demonstrationEnabled) { Text(label) }
        }
    }
}
