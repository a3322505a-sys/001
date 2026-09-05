package com.a3322505a.guitarlearning.learning

import kotlin.test.*

class LearningPresentationTest {
    @Test fun lowRegionCountsAllNineNodesAndDoesNotUnlockPlannedLessons() {
        val s = LearnerState(progress = listOf("p01", "p02", "p03").associateWith { NodeProgress(masteredAt = 1L) })
        assertEquals("3/9 已掌握", FretboardRegion.LOW.progressLabel(s))
        assertEquals("规划中", FretboardRegion.MIDDLE.progressLabel(s))
        assertEquals("0–12 品", FretboardRegion.FULL.rangeLabel)
        assertEquals(Curriculum.nodes.filter { it.category == Category.FRETBOARD }.map { it.id }.toSet(),
            FretboardRegion.entries.flatMap { it.nodeIds }.toSet())
        assertEquals(NodeVisualState.PLANNED, nodeVisualState(s, Curriculum.node("p04")))
        assertFalse(Curriculum.available(s, Curriculum.node("p04")))
    }

    @Test fun reviewKeepsMasteryAndDependentAvailability() {
        val s = LearnerState(progress = mapOf("g00" to NodeProgress(masteredAt = 1L, needsReview = true)))
        assertEquals(NodeVisualState.REVIEW, nodeVisualState(s, Curriculum.node("g00")))
        assertTrue(Curriculum.mastered(s, "g00"))
        assertEquals(NodeVisualState.AVAILABLE, nodeVisualState(s, Curriculum.node("n00")))
        assertEquals(NodeVisualState.LOCKED, nodeVisualState(s, Curriculum.node("p01")))
        assertEquals(NodeVisualState.CURRENT, nodeVisualState(s.copy(currentNode = "n00", sessionId = "session"), Curriculum.node("n00")))
    }
}
