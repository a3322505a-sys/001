package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.test.*

class MajorScalePatternsTest {
    @Test fun referenceShapesKeepTheirActualPitchesAndOverlaps() {
        assertEquals(listOf("mi", "sol", "la", "si", "re"), MajorScalePatterns.all.map { it.id })
        assertEquals(listOf("C", "A", "G", "E", "D"), MajorScalePatterns.all.map { it.cagedName })
        assertEquals(listOf(3, 6, 8, 10, 13), MajorScalePatterns.all.map { it.lastFret })
        for ((index, shape) in MajorScalePatterns.all.withIndex()) {
            assertEquals(6, shape.fretsByString.size)
            assertTrue(shape.positions.all { MusicFacts.majorDegree(MusicFacts.midi(it.string, it.fret), 0) != null })
            assertTrue(shape.roots.all { MusicFacts.note(it.string, it.fret) == "C" })
            assertTrue(ScalePatternLessons.tasks(shape).all { task ->
                task.sequence.isNotEmpty() && task.sequence.indices.all { AnswerEvaluator.validPositions(task, it).isNotEmpty() }
            })
            if (index < 4) assertTrue(shape.positions.intersect(MajorScalePatterns.all[index + 1].positions.toSet()).isNotEmpty())
        }
        assertEquals(60, MusicFacts.midi(2, 1))
        assertEquals(60, MusicFacts.midi(3, 5))
        assertEquals(60, MusicFacts.midi(4, 10))
        assertEquals(72, MusicFacts.midi(2, 13))
    }

    @Test fun relationshipRoutesStayGroupedAndPauseResumesAtTheSameTarget() {
        val shape = MajorScalePatterns.all.first()
        val tasks = ScalePatternLessons.tasks(shape)
        val thirds = tasks.first { it.skillId.endsWith(":thirds") }.relation!!.targetPitches
        assertEquals(4, thirds[1] - thirds[0])
        assertEquals(3, thirds[3] - thirds[2])
        val motif = tasks.first { it.skillId.endsWith(":motif") }
        assertEquals(9, motif.sequence.size)
        assertTrue(motif.sequence.all { it.kind == ConstraintKind.COORDINATE })

        val coordinator = LearningCoordinator()
        var state = coordinator.start(LearnerState(), "pattern-mi", 100)
        val first = state.active!!.task
        assertTrue(first.guided)
        state = coordinator.answer(state, first.sequence.first().coordinate, now = 101)
        assertEquals(1, state.active!!.sequenceIndex)
        state = LearningCodec.decode(LearningCodec.encode(state))
        assertEquals(first.id, state.active!!.task.id)
        assertEquals(1, state.active!!.sequenceIndex)
        assertFalse(ScalePatternLessons.passed(state, "pattern-mi"))
        val active = state.active!!
        val wrongPitch = requireNotNull(active.task.relation).targetPitches.map { it + 1 }
        val invalid = state.copy(active = active.copy(task = active.task.copy(
            relation = active.task.relation!!.copy(targetPitches = wrongPitch))))
        assertFailsWith<IllegalArgumentException> { LearningCodec.decode(LearningCodec.encode(invalid)) }
    }

    @Test fun relativeMinorChangesTheTonicAndMovableShapeSpellsNewKeys() {
        for (pattern in MajorScalePatterns.all) {
            val minor = ScalePatternLessons.tasks(pattern).first { it.skillId.endsWith(":minor") }
            assertEquals(9, minor.tonicPitchClass)
            assertEquals(listOf(57, 60, 64, 60, 57), minor.relation!!.targetPitches)
        }
        for (key in ScalePatternLessons.transposed) {
            val tasks = ScalePatternLessons.tasks(key.id)
            assertTrue(tasks.isNotEmpty())
            assertTrue(tasks.flatMap { it.sequence }.all { rule ->
                val c = requireNotNull(rule.coordinate)
                c.fret in 0..15 && MusicFacts.majorDegree(MusicFacts.midi(c.string, c.fret), key.tonic) != null
            })
            val pitches = tasks.flatMap { it.relation!!.targetPitches }.toSet()
            if (key.tonic == 7) {
                assertTrue(pitches.any { it % 12 == 6 })
                assertEquals("F♯", key.spelling(66))
            } else {
                assertTrue(pitches.any { it % 12 == 10 })
                assertEquals("B♭", key.spelling(70))
            }
        }
    }
}
