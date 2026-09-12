package com.voxcoach.core.domain.model

/**
 * Hardcoded MiMo-V2.5 model IDs. Settings only collect baseUrl + apiKey.
 */
object MimoDefaults {
    const val BASE_URL = "https://api.xiaomimimo.com/v1"
    const val CHAT_MODEL = "mimo-v2.5"
    const val ASR_MODEL = "mimo-v2.5-asr"
    const val TTS_MODEL = "mimo-v2.5-tts"
    const val TTS_VOICE = "Chloe"
    const val TTS_STYLE =
        "Speak in clear British English as an IELTS examiner. Calm, natural conversational pace."
    const val ASR_SAMPLE_RATE = 16_000
    const val TTS_SAMPLE_RATE = 24_000
    const val ASR_LANGUAGE = "en"
}
