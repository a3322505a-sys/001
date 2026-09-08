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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.lines.forEach { Text(it, fontSize = 14.sp) }
        state.demonstrationLabel?.let { label ->
            OutlinedButton(onClick = onDemonstrate, enabled = state.demonstrationEnabled) { Text(label) }
        }
    }
}
