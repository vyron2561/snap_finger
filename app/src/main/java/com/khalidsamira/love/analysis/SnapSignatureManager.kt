package com.khalidsamira.love.analysis

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.sqrt

class SnapSignatureManager(context: Context) {
    companion object {
        private const val TAG = "SnapSignatureManager"
        private const val SIGNATURE_PREFS = "snap_signature"
        private const val PROFILE_KEY_V2 = "template_v2"
        private const val SAMPLES_KEY_V2 = "samples_v2"
        private const val THRESHOLD_KEY_V2 = "threshold_v2"
        private const val LEGACY_TEMPLATE_KEY = "template"
        private const val LEGACY_SAMPLES_KEY = "samples"
        private const val LEARNING_KEY = "learning"
        private const val COUNT_KEY = "learn_count"
        const val REQUIRED_SAMPLES = 10
        const val FEATURE_SIZE_V2 = 22
        private const val FEATURE_SIZE_V1 = 16
        private const val K_NEIGHBORS = 3
        private const val V1_THRESHOLD = 0.90f
        private const val MIN_THRESHOLD = 0.86f
        private const val MAX_THRESHOLD = 0.97f
        private const val THRESHOLD_FACTOR = 0.96f
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SIGNATURE_PREFS, Context.MODE_PRIVATE)

    fun startLearning() {
        prefs.edit()
            .putBoolean(LEARNING_KEY, true)
            .putInt(COUNT_KEY, 0)
            .remove(SAMPLES_KEY_V2)
            .remove(PROFILE_KEY_V2)
            .remove(THRESHOLD_KEY_V2)
            .remove(LEGACY_TEMPLATE_KEY)
            .remove(LEGACY_SAMPLES_KEY)
            .apply()
        android.util.Log.i(TAG, "Learning started (cleared all profiles)")
    }

    fun isLearning(): Boolean = prefs.getBoolean(LEARNING_KEY, false)
    fun getLearnedCount(): Int = prefs.getInt(COUNT_KEY, 0)

    fun hasProfile(): Boolean =
        prefs.getString(PROFILE_KEY_V2, null) != null ||
            prefs.getString(LEGACY_TEMPLATE_KEY, null) != null

    fun stopLearning() {
        prefs.edit().putBoolean(LEARNING_KEY, false).remove(SAMPLES_KEY_V2).apply()
    }

    @Deprecated("Use createRichFeature(samples, analysis) for v2 profiles")
    fun createFeature(samples: ShortArray): FloatArray {
        val bins = FloatArray(FEATURE_SIZE_V1)
        for (i in samples.indices) {
            val bin = (i * FEATURE_SIZE_V1 / samples.size).coerceIn(0, bins.lastIndex)
            val value = samples[i].toFloat() / 32768f
            bins[bin] += value * value
        }
        normalizeInPlace(bins)
        return bins
    }

    fun createRichFeature(samples: ShortArray, analysis: SnapAnalysis): FloatArray {
        val bins = FloatArray(FEATURE_SIZE_V1)
        for (i in samples.indices) {
            val bin = (i * FEATURE_SIZE_V1 / samples.size).coerceIn(0, bins.lastIndex)
            val value = samples[i].toFloat() / 32768f
            bins[bin] += value * value
        }
        normalizeInPlace(bins)

        val rich = FloatArray(6)
        rich[0] = analysis.spectral.zeroCrossingRate.toFloat()
        rich[1] = analysis.spectral.highFreqRatio.toFloat()
        rich[2] = analysis.spectral.lowFreqRatio.toFloat()
        rich[3] = (analysis.spectral.spectralCentroid / 8000.0).toFloat().coerceIn(0f, 1f)
        rich[4] = (analysis.crest / 10.0).toFloat().coerceIn(0f, 1f)
        rich[5] = analysis.decayRatio.toFloat().coerceIn(0f, 1f)
        normalizeInPlace(rich)

        val combined = FloatArray(FEATURE_SIZE_V2)
        for (i in 0 until FEATURE_SIZE_V1) combined[i] = bins[i] * 0.35f
        for (i in 0 until 6) combined[FEATURE_SIZE_V1 + i] = rich[i] * 0.65f
        normalizeInPlace(combined)
        return combined
    }

    private fun normalizeInPlace(v: FloatArray) {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm < 0.000001f) norm = 1f
        for (i in v.indices) v[i] /= norm
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / sqrt(na * nb).coerceAtLeast(0.000001f)
    }

    fun addLearningSample(feature22: FloatArray): Boolean {
        if (!isLearning() || feature22.size != FEATURE_SIZE_V2) return false

        val previous = prefs.getString(SAMPLES_KEY_V2, "").orEmpty()
        val encoded = feature22.joinToString(",")
        val allEncodedSamples = if (previous.isBlank()) encoded else "$previous;$encoded"
        val count = prefs.getInt(COUNT_KEY, 0) + 1
        val editor = prefs.edit()
            .putString(SAMPLES_KEY_V2, allEncodedSamples)
            .putInt(COUNT_KEY, count)

        android.util.Log.i(TAG, "Learning rich sample $count/$REQUIRED_SAMPLES")
        if (count < REQUIRED_SAMPLES) {
            editor.apply()
            return false
        }

        // Parse the updated value so the tenth sample is included before persistence.
        val rows = allEncodedSamples.split(";").mapNotNull { row ->
            row.split(",").mapNotNull { it.toFloatOrNull() }
                .takeIf { it.size == FEATURE_SIZE_V2 }?.toFloatArray()
        }
        if (rows.size < REQUIRED_SAMPLES) {
            android.util.Log.w(TAG, "Learning finished but only ${rows.size} valid samples; aborting")
            editor.putBoolean(LEARNING_KEY, false).remove(SAMPLES_KEY_V2).apply()
            return false
        }

        var worstSelfMatch = 1.0f
        for (i in rows.indices) {
            val similarities = rows.filterIndexed { index, _ -> index != i }
                .map { cosineSimilarity(rows[i], it) }
                .sortedDescending()
                .take(K_NEIGHBORS)
            if (similarities.isEmpty()) continue
            val averageTopK = similarities.average().toFloat()
            if (averageTopK < worstSelfMatch) worstSelfMatch = averageTopK
        }

        val adaptiveThreshold = (worstSelfMatch * THRESHOLD_FACTOR)
            .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        android.util.Log.i(
            TAG,
            "Profile v2 built: worstSelfMatch=$worstSelfMatch adaptiveThreshold=$adaptiveThreshold"
        )

        val profileData = rows.joinToString(";") { it.joinToString(",") }
        editor.putString(PROFILE_KEY_V2, profileData)
            .putFloat(THRESHOLD_KEY_V2, adaptiveThreshold)
            .putBoolean(LEARNING_KEY, false)
            .remove(SAMPLES_KEY_V2)
            .apply()
        return true
    }

    fun shouldAccept(feature: FloatArray): Boolean {
        val v2Profile = prefs.getString(PROFILE_KEY_V2, null)
        if (v2Profile != null && feature.size == FEATURE_SIZE_V2) {
            return matchV2(feature, v2Profile)
        }

        val v1Template = prefs.getString(LEGACY_TEMPLATE_KEY, null)
        if (v1Template != null) return matchV1(feature, v1Template)

        return true
    }

    private fun matchV2(feature22: FloatArray, profileData: String): Boolean {
        val samples = profileData.split(";").mapNotNull { row ->
            row.split(",").mapNotNull { it.toFloatOrNull() }
                .takeIf { it.size == FEATURE_SIZE_V2 }?.toFloatArray()
        }
        if (samples.size < K_NEIGHBORS) {
            android.util.Log.w(TAG, "v2 profile has <${K_NEIGHBORS} samples; accepting")
            return true
        }

        val threshold = prefs.getFloat(THRESHOLD_KEY_V2, 0.92f)
        val topK = samples.map { cosineSimilarity(feature22, it) }
            .sortedDescending().take(K_NEIGHBORS)
        val averageTopK = topK.average().toFloat()
        android.util.Log.i(
            TAG,
            "v2 match: avgTop${K_NEIGHBORS}=$averageTopK threshold=$threshold topK=${topK.joinToString(",")}"
        )
        return averageTopK >= threshold
    }

    private fun matchV1(feature: FloatArray, template: String): Boolean {
        val expected = template.split(",").mapNotNull { it.toFloatOrNull() }
        if (expected.size != FEATURE_SIZE_V1) return true
        if (feature.size != FEATURE_SIZE_V1 && feature.size != FEATURE_SIZE_V2) return true

        val v16 = FloatArray(FEATURE_SIZE_V1) { feature[it] }
        normalizeInPlace(v16)
        val similarity = cosineSimilarity(v16, expected.toFloatArray())
        android.util.Log.i(TAG, "v1 legacy match: similarity=$similarity")
        return similarity >= V1_THRESHOLD
    }
}
