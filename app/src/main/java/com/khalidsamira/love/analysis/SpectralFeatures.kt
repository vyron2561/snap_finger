package com.khalidsamira.love.analysis

data class SpectralFeatures(
    val spectralCentroid: Double,
    val zeroCrossingRate: Double,
    val highFreqRatio: Double,
    val lowFreqRatio: Double
)

object SpectralFeatureExtractor {
    fun extract(samples: ShortArray): SpectralFeatures {
        // Zero-crossing rate
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0) != (samples[i] >= 0)) crossings++
        }
        val zcr = crossings.toDouble() / samples.size

        // Energy in 3 bands via cascaded one-pole low-pass filters.
        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        var totalEnergy = 0.0
        var lp1 = 0.0
        var lp2 = 0.0
        val a1 = 0.15
        val a2 = 0.55

        for (s in samples) {
            val v = s.toDouble()
            lp1 += a1 * (v - lp1)
            lp2 += a2 * (v - lp2)
            val low = lp1
            val mid = lp2 - lp1
            val high = v - lp2
            lowEnergy += low * low
            midEnergy += mid * mid
            highEnergy += high * high
            totalEnergy += v * v
        }

        val total = maxOf(totalEnergy, 1.0)
        val centroid =
            (lowEnergy / total) * 500.0 +
            (midEnergy / total) * 3000.0 +
            (highEnergy / total) * 10000.0

        return SpectralFeatures(
            spectralCentroid = centroid,
            zeroCrossingRate = zcr,
            highFreqRatio = highEnergy / total,
            lowFreqRatio = lowEnergy / total
        )
    }
}
