package com.khalidsamira.love.analysis

data class SnapAnalysis(
    val peak: Int,
    val rms: Double,
    val crest: Double,
    val lowRatio: Double,
    val concentration: Double,
    val activeChunks: Int,
    val maxChunkRms: Double,
    val otherAverage: Double,
    val decayRatio: Double,
    val maxIndex: Int,
    val looksLikeSnap: Boolean
)
