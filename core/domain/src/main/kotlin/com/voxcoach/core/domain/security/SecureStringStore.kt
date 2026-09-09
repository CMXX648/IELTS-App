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

/** Canonical key for ST-01 LLM apiKey in [SecureStringStore]. */
object SecureKeys {
    const val LLM_API_KEY = "llm_api_key"
}
