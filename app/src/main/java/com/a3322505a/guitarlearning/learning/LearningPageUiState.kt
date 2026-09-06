package com.a3322505a.guitarlearning.learning

data class ChoiceUi(val id: String, val title: String)
data class HomeEntryUi(val id: String, val title: String, val subtitle: String, val actionLabel: String = "", val startNode: String? = null, val resume: Boolean = false)
data class NodeRowUi(val id: String, val title: String, val status: NodeVisualState, val current: Boolean, val statusLabel: String,
    val startLabel: String?, val prerequisites: String = "")
data class RegionUi(val id: String, val title: String, val progress: String, val rows: List<NodeRowUi>, val practiceIds: List<String>, val note: String? = null, val startLabel: String? = null)
data class CatalogSectionUi(val title: String?, val rows: List<NodeRowUi> = emptyList(), val regions: List<RegionUi> = emptyList())
data class CatalogUiState(val sections: List<CatalogSectionUi>, val showExamples: Boolean = false)
data class InfoPanelUi(val title: String, val subtitle: String? = null, val lines: List<String> = emptyList(), val nodes: List<ChoiceUi> = emptyList())
data class NodeDetailUiState(val row: NodeRowUi, val description: String, val startLabel: String?, val canPractice: Boolean, val panels: List<InfoPanelUi>, val recordLines: List<String>, val physical: List<PhysicalExercise> = emptyList())
data class SettingsUiState(val themeId: String, val fingeringMode: String, val soundEnabled: Boolean, val busy: Boolean, val notice: String?, val version: String)
data class ChordExamplesUiState(val choices: List<ChoiceUi>, val title: String, val board: FretboardUiState, val fingeringMode: String, val soundEnabled: Boolean, val busy: Boolean, val audio: AudioUiState)
data class PracticeUiState(val nodes: List<ChoiceUi>, val selected: List<String>, val kinds: List<ChoiceUi>, val kind: String?, val busy: Boolean, val canStart: Boolean, val startLabel: String)
