package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.core.CoreLayer
import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.StorageState
import com.a3322505a.guitarlearning.training.TrainingLayer
import com.a3322505a.guitarlearning.ui.UiLayer
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectStructureTest {
    @Test
    fun layersHaveStableBoundaries() {
        assertEquals("Guitar Core", CoreLayer.NAME)
        assertEquals("Training Engine", TrainingLayer.NAME)
        assertEquals("UI", UiLayer.NAME)
    }

    @Test
    fun storageBoundaryRoundTripsState() {
        val store = InMemoryTrainingStore()
        store.saveState(StorageState(version = 1))

        assertEquals(StorageState(version = 1), store.loadState())
    }
}
