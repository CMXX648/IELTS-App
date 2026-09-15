package com.voxcoach.core.domain.security

/**
 * At-rest secret storage for small strings (e.g. LLM apiKey).
 * Android impl: AndroidKeyStore AES/GCM (docs/04 §5.1).
 * Tests may inject an in-memory impl.
 */
interface SecureStringStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

object SecureKeys {
    const val LLM_API_KEY = "llm_api_key"

    /** SY device key (docs/05 §4.1) — Keystore-wrapped like ST-01 apiKey. */
    const val SYNC_DEVICE_KEY = "sync_device_key"
}
