package com.khalidsamira.love.analysis

import kotlin.math.abs
import kotlin.math.sqrt

class SnapAnalyzer {
    companion object {
        private const val CHUNK_SIZE = 64
        private const val NUM_CHUNKS = 16
        private const val LOWPASS_ALPHA = 0.16
        private const val INITIAL_NOISE_FLOOR = 220.0

        private const val PEAK_THRESHOLD = 5000
        private const val RMS_ABS_MIN = 300.0
        private const val RMS_NOISE_MULT = 1.9
        private const val CREST_MIN = 3.0
        private const val LOW_RATIO_MAX = 0.78
        private const val CONCENTRATION_MIN = 0.08
        private const val ACTIVE_CHUNKS_MAX = 4
        private const val MAX_INDEX_LIMIT = 13
        private const val DECAY_RATIO_MAX = 0.38
        private const val OTHER_RATIO_MIN = 1.7
    }

    var noiseFloor: Double = INITIAL_NOISE_FLOOR
        private set

    fun analyze(samples: ShortArray): SnapAnalysis {
        val chunkEnergy = DoubleArray(NUM_CHUNKS)
        val chunkRms = DoubleArray(NUM_CHUNKS)

        var sumSquares = 0.0
        var lowFrequencyEnergy = 0.0
        var peak = 0
        var lowPass = 0.0

        for (i in samples.indices) {
            val value = samples[i].toInt()
            val absolute = abs(value)
            if (absolute > peak) peak = absolute

            val squared = value.toDouble() * value.toDouble()
            sumSquares += squared

            lowPass += LOWPASS_ALPHA * (value - lowPass)
            lowFrequencyEnergy += lowPass * lowPass

            val chunk = i / CHUNK_SIZE
            if (chunk < NUM_CHUNKS) chunkEnergy[chunk] += squared
        }

        val rms = sqrt(sumSquares / samples.size)
        val lowRatio = lowFrequencyEnergy / maxOf(sumSquares, 1.0)
        val crest = peak / maxOf(rms, 1.0)

        var maxIndex = 0
        var totalChunkEnergy = 0.0
        for (i in 0 until NUM_CHUNKS) {
            chunkRms[i] = sqrt(chunkEnergy[i] / CHUNK_SIZE.toDouble())
            totalChunkEnergy += chunkEnergy[i]
            if (chunkEnergy[i] > chunkEnergy[maxIndex]) maxIndex = i
        }

        val maxChunkRms = chunkRms[maxIndex]
        val concentration = chunkEnergy[maxIndex] / maxOf(totalChunkEnergy, 1.0)

        var activeChunks = 0
        var otherTotal = 0.0
        var otherCount = 0
        for (i in 0 until NUM_CHUNKS) {
            if (chunkRms[i] > maxChunkRms * 0.30) activeChunks++
            if (i != maxIndex) {
                otherTotal += chunkRms[i]
                otherCount++
            }
        }
        val otherAverage = otherTotal / maxOf(otherCount, 1)

        val decayRms = if (maxIndex <= MAX_INDEX_LIMIT) {
            (chunkRms[maxIndex + 1] + chunkRms[maxIndex + 2]) / 2.0
        } else {
            Double.MAX_VALUE
        }
        val decayRatio = decayRms / maxOf(maxChunkRms, 1.0)

        val looksLikeSnap =
            peak >= PEAK_THRESHOLD &&
                rms >= maxOf(RMS_ABS_MIN, noiseFloor * RMS_NOISE_MULT) &&
                crest >= CREST_MIN &&
                lowRatio <= LOW_RATIO_MAX &&
                concentration >= CONCENTRATION_MIN &&
                activeChunks <= ACTIVE_CHUNKS_MAX &&
                maxIndex <= MAX_INDEX_LIMIT &&
                decayRatio <= DECAY_RATIO_MAX &&
                maxChunkRms >= otherAverage * OTHER_RATIO_MIN

        // Noise floor adaptation (same as original detectSnaps)
        if (rms < maxOf(noiseFloor * 1.8, 700.0)) {
            noiseFloor = noiseFloor * 0.94 + rms * 0.06
        }

        return SnapAnalysis(
            peak = peak,
            rms = rms,
            crest = crest,
            lowRatio = lowRatio,
            concentration = concentration,
            activeChunks = activeChunks,
            maxChunkRms = maxChunkRms,
            otherAverage = otherAverage,
            decayRatio = decayRatio,
            maxIndex = maxIndex,
            looksLikeSnap = looksLikeSnap
        )
    }
}
