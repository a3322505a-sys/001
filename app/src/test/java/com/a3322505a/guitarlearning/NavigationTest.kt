package com.a3322505a.guitarlearning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationTest {
    @Test
    fun onlyNoteNameTrainingUsesLandscape() {
        assertTrue(usesLandscapeLayout(AppDestination.NoteName))
        assertTrue(usesImmersiveSystemBars(AppDestination.NoteName))
        assertFalse(usesLandscapeLayout(AppDestination.Home))
        assertFalse(usesLandscapeLayout(AppDestination.NoteNameRange))
        assertFalse(usesImmersiveSystemBars(AppDestination.NoteNameRange))
        assertFalse(usesLandscapeLayout(AppDestination.CombinedMapping))
    }

    @Test
    fun trainingBackStacksIncludeTheirSelectionScreens() {
        assertEquals(AppDestination.NoteNameRange, previousDestination(AppDestination.NoteName))
        assertEquals(AppDestination.Home, previousDestination(AppDestination.NoteNameRange))
        assertEquals(AppDestination.Home, previousDestination(AppDestination.CombinedMapping))
    }
}
