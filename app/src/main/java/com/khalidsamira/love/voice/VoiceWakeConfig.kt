package com.khalidsamira.love.voice

object VoiceWakeConfig {
    const val PREFS = "voice_wake_prefs"
    const val KEY_ENABLED = "voice_wake_enabled"
    const val KEY_MODEL_READY = "voice_model_ready"
    const val KEY_START_HOUR = "voice_start_hour"
    const val KEY_END_HOUR = "voice_end_hour"
    const val KEY_KEYWORD = "voice_keyword"
    const val DEFAULT_START_HOUR = 0
    const val DEFAULT_END_HOUR = 0
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    const val SAMPLE_RATE = 16000
    const val CHUNK_SIZE = 4000
    const val VAD_BASE_THRESHOLD = 500.0
    const val VAD_NOISE_FACTOR = 2.5
    const val GRAMMAR_JSON = """["kofasha", "kofaxa", "wake up", "computer", "[unk]"]"""
    const val NOTIFICATION_ID = 913
    const val CHANNEL_ID = "voice_wake_channel"
}
