package com.khalidsamira.love.analysis

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.sqrt

class SnapSignatureManager(context: Context) {
    companion object {
        private const val TAG = "SnapSignatureManager"
        private const val SIGNATURE_PREFS = "snap_signature"
        private const val SIGNATURE_KEY = "template"
        private const val SAMPLE_KEY = "samples"
        private const val COUNT_KEY = "learn_count"
        private const val LEARNING_KEY = "learning"
        const val REQUIRED_SAMPLES = 10
        private const val SIMILARITY_THRESHOLD = 0.90f
        private const val FEATURE_SIZE = 16
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SIGNATURE_PREFS, Context.MODE_PRIVATE)

    fun isLearning(): Boolean = prefs.getBoolean(LEARNING_KEY, false)

    fun getLearnedCount(): Int = prefs.getInt(COUNT_KEY, 0)

    fun hasProfile(): Boolean = prefs.getString(SIGNATURE_KEY, null) != null

    fun stopLearning() {
        prefs.edit().putBoolean(LEARNING_KEY, false).remove(SAMPLE_KEY).apply()
    }

    fun createFeature(samples: ShortArray): FloatArray {
        val bins = FloatArray(FEATURE_SIZE)
        for (i in samples.indices) {
            val bin = (i * FEATURE_SIZE / samples.size).coerceIn(0, bins.lastIndex)
            val value = samples[i].toFloat() / 32768f
            bins[bin] += value * value
        }
        var norm = sqrt(bins.sumOf { (it * it).toDouble() }).toFloat()
        if (norm < 0.000001f) norm = 1f
        for (i in bins.indices) bins[i] /= norm
        return bins
    }

    // Returns true if learning is now complete (10 samples collected)
    fun addLearningSample(feature: FloatArray): Boolean {
        if (!isLearning()) return false

        val previous = prefs.getString(SAMPLE_KEY, "").orEmpty()
        val encoded = feature.joinToString(",")
        val count = prefs.getInt(COUNT_KEY, 0) + 1

        val editor = prefs.edit()
            .putString(SAMPLE_KEY, if (previous.isBlank()) encoded else previous + ";" + encoded)
            .putInt(COUNT_KEY, count)

        android.util.Log.i(TAG, "Learning snap $count/$REQUIRED_SAMPLES")

        if (count >= REQUIRED_SAMPLES) {
            val rows = prefs.getString(SAMPLE_KEY, "").orEmpty()
                .split(";")
                .mapNotNull { row ->
                    row.split(",").mapNotNull { it.toFloatOrNull() }
                        .takeIf { it.size == FEATURE_SIZE }
                }
            if (rows.size == REQUIRED_SAMPLES) {
                val average = FloatArray(FEATURE_SIZE) { index ->
                    rows.map { it[index] }.average().toFloat()
                }
                editor.putString(SIGNATURE_KEY, average.joinToString(","))
                    .putBoolean(LEARNING_KEY, false)
                    .remove(SAMPLE_KEY)
                editor.apply()
                android.util.Log.i(TAG, "Personal snap profile saved")
                return true
            }
        }
        editor.apply()
        return false
    }

    // true = accept (no profile stored, or similarity >= 0.90)
    fun shouldAccept(feature: FloatArray): Boolean {
        val saved = prefs.getString(SIGNATURE_KEY, null) ?: return true
        val expected = saved.split(",").mapNotNull { it.toFloatOrNull() }
        if (expected.size != FEATURE_SIZE) return true

        var dot = 0f
        var left = 0f
        var right = 0f
        for (i in feature.indices) {
            dot += feature[i] * expected[i]
            left += feature[i] * feature[i]
            right += expected[i] * expected[i]
        }
        val similarity = dot / (sqrt(left * right).coerceAtLeast(0.000001f))
        android.util.Log.i(TAG, "Personal snap similarity=$similarity")
        return similarity >= SIMILARITY_THRESHOLD
    }
}
