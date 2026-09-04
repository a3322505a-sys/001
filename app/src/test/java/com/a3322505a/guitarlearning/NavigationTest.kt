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
        assertFalse(usesLandscapeLayout(AppDestination.ReadingMenu))
        assertFalse(usesLandscapeLayout(AppDestination.TabReading))
        assertFalse(usesLandscapeLayout(AppDestination.StaffReading))
    }

    @Test
    fun trainingBackStacksIncludeTheirSelectionScreens() {
        assertEquals(AppDestination.NoteNameRange, previousDestination(AppDestination.NoteName))
        assertEquals(AppDestination.Home, previousDestination(AppDestination.NoteNameRange))
        assertEquals(AppDestination.Home, previousDestination(AppDestination.CombinedMapping))
        assertEquals(AppDestination.Home, previousDestination(AppDestination.ReadingMenu))
        assertEquals(AppDestination.ReadingMenu, previousDestination(AppDestination.TabReading))
        assertEquals(AppDestination.ReadingMenu, previousDestination(AppDestination.StaffReading))
    }
}
