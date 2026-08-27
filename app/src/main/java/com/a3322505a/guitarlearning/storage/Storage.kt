package com.a3322505a.guitarlearning.storage

/** Minimal local-storage boundary; concrete records are added in later checkpoints. */
interface TrainingStore {
    fun loadState(): StorageState
    fun saveState(state: StorageState)
}

data class StorageState(val version: Int = 1)

class InMemoryTrainingStore(initialState: StorageState = StorageState()) : TrainingStore {
    private var state = initialState

    override fun loadState(): StorageState = state

    override fun saveState(state: StorageState) {
        this.state = state
    }
}
