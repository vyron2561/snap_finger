package com.khalidsamira.love.analysis

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class SnapAnalyzerClapTest {

    @Test
    fun highFrequencyAlternating_isClapLike() {
        // Rapid alternation = high ZCR + high-freq energy.
        val samples = ShortArray(1024) { i ->
            if (i % 2 == 0) 15000 else -15000
        }
        val analyzer = SnapAnalyzer()
        val result = analyzer.analyze(samples)
        assertTrue(
            "Alternating noise should be classified as clap-like",
            result.isClapLike
        )
    }

    @Test
    fun lowFrequencyBurst_isNotClapLike() {
        val samples = ShortArray(1024) { i ->
            (8000 * kotlin.math.sin(2.0 * Math.PI * 200.0 * i / 16000.0))
                .toInt().toShort()
        }
        val analyzer = SnapAnalyzer()
        val result = analyzer.analyze(samples)
        assertFalse(
            "Low-frequency burst should NOT be classified as clap-like",
            result.isClapLike
        )
    }
}
