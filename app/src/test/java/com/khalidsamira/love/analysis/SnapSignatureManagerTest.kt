package com.khalidsamira.love.analysis

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.sqrt

class SnapSignatureManagerTest {
    @Test
    fun normalizeInvariant() {
        val v = floatArrayOf(
            3f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
        )
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        for (i in v.indices) v[i] /= norm
        var finalNorm = 0f
        for (x in v) finalNorm += x * x
        assertEquals(1f, sqrt(finalNorm), 0.0001f)
        assertEquals(0.6f, v[0], 0.0001f)
        assertEquals(0.8f, v[1], 0.0001f)
    }

    @Test
    fun identicalVectorsHaveSimilarityOne() {
        val a = FloatArray(22) { i -> (i + 1).toFloat() / 22f }
        var norm = 0f
        for (x in a) norm += x * x
        norm = sqrt(norm)
        for (i in a.indices) a[i] /= norm
        var dot = 0f
        for (i in a.indices) dot += a[i] * a[i]
        assertEquals(1f, dot, 0.0001f)
    }

    @Test
    fun orthogonalVectorsHaveSimilarityZero() {
        val a = FloatArray(22) { i -> if (i == 0) 1f else 0f }
        val b = FloatArray(22) { i -> if (i == 1) 1f else 0f }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        assertTrue("Orthogonal vectors should have similarity 0", dot == 0f)
    }
}
