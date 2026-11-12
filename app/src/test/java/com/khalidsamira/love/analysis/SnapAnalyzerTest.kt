package com.khalidsamira.love.analysis

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SnapAnalyzerTest {

    @Test
    fun silence_isNotSnap() {
        val analyzer = SnapAnalyzer()
        val samples = ShortArray(1024) { 0 }
        val result = analyzer.analyze(samples)
        assertFalse(result.looksLikeSnap)
        assertEquals(0, result.peak)
    }

    @Test
    fun steady_loud_noise_isNotSnap() {
        // Constant amplitude => crest ~ 1.0, way below the 3.0 threshold.
        val analyzer = SnapAnalyzer()
        val samples = ShortArray(1024) { 20000 }
        val result = analyzer.analyze(samples)
        assertFalse(result.looksLikeSnap)
    }

    @Test
    fun noise_floor_unchanged_for_loud_input() {
        val analyzer = SnapAnalyzer()
        analyzer.analyze(ShortArray(1024) { 20000 })
        assertEquals(220.0, analyzer.noiseFloor, 0.01)
    }

    @Test
    fun noise_floor_decreases_in_quiet_room() {
        val analyzer = SnapAnalyzer()
        repeat(50) {
            analyzer.analyze(ShortArray(1024) { 20 })
        }
        assertTrue(analyzer.noiseFloor < 100.0)
    }
}
