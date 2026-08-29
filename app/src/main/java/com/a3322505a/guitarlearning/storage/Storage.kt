package com.a3322505a.guitarlearning.storage

/** Local-only storage boundary for the V0.2.2 learning records. */
interface TrainingStore {
    fun loadState(): StorageState
    fun saveState(state: StorageState)
    fun loadSettings(): Settings
    fun saveSettings(settings: Settings)
    fun upsertKnowledgeItem(item: KnowledgeItem)
    fun findKnowledgeItem(id: String): KnowledgeItem?
    fun loadKnowledgeItems(): List<KnowledgeItem>
    fun saveProgress(progress: Progress)
    fun loadProgress(knowledgeItemId: String): Progress?
    fun loadProgress(): List<Progress>
    fun saveSession(session: Session)
    fun loadSessions(): List<Session>
}

data class StorageState(val version: Int = 1)

class InMemoryTrainingStore(initialState: StorageState = StorageState()) : TrainingStore {
    private var state = initialState
    private var settings = Settings()
    private val knowledgeItems = linkedMapOf<String, KnowledgeItem>()
    private val progress = linkedMapOf<String, Progress>()
    private val sessions = linkedMapOf<String, Session>()

    override fun loadState(): StorageState = state

    override fun saveState(state: StorageState) {
        this.state = state
    }

    override fun loadSettings(): Settings = settings

    override fun saveSettings(settings: Settings) {
        this.settings = settings
    }

    override fun upsertKnowledgeItem(item: KnowledgeItem) {
        knowledgeItems[item.id] = item
    }

    override fun findKnowledgeItem(id: String): KnowledgeItem? = knowledgeItems[id]

    override fun loadKnowledgeItems(): List<KnowledgeItem> = knowledgeItems.values.toList()

    override fun saveProgress(progress: Progress) {
        this.progress[progress.knowledgeItemId] = progress
    }

    override fun loadProgress(knowledgeItemId: String): Progress? = progress[knowledgeItemId]

    override fun loadProgress(): List<Progress> = progress.values.toList()

    override fun saveSession(session: Session) {
        sessions[session.id] = session
    }

    override fun loadSessions(): List<Session> = sessions.values.toList()
}
