package com.voxcoach.core.data.settings

import com.google.common.truth.Truth.assertThat
import com.voxcoach.core.domain.security.SecureKeys
import com.voxcoach.core.domain.security.SecureStringStore
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Documents one-shot plaintext → secure migration rules (ST-01). */
class ApiKeyMigrationLogicTest {

    @Test
    fun migrateWhenPlainPresentAndSecureEmpty() = runBlocking {
        val secure = InMemorySecureStringStore()
        val plain = " sk-legacy "
        val shouldCopy = plain.isNotBlank() && secure.get(SecureKeys.LLM_API_KEY).isNullOrBlank()
        assertThat(shouldCopy).isTrue()
        if (shouldCopy) secure.put(SecureKeys.LLM_API_KEY, plain.trim())
        assertThat(secure.get(SecureKeys.LLM_API_KEY)).isEqualTo("sk-legacy")
    }

    @Test
    fun doNotOverwriteExistingSecureValue() = runBlocking {
        val secure = InMemorySecureStringStore()
        secure.put(SecureKeys.LLM_API_KEY, "sk-already")
        val plain: String? = "sk-legacy"
        val shouldCopy = !plain.isNullOrBlank() && secure.get(SecureKeys.LLM_API_KEY).isNullOrBlank()
        assertThat(shouldCopy).isFalse()
        assertThat(secure.get(SecureKeys.LLM_API_KEY)).isEqualTo("sk-already")
    }

    @Test
    fun blankPlainSkipsMigration() = runBlocking {
        val secure = InMemorySecureStringStore()
        val plain: String? = "   "
        val shouldCopy = !plain.isNullOrBlank() && secure.get(SecureKeys.LLM_API_KEY).isNullOrBlank()
        assertThat(shouldCopy).isFalse()
        assertThat(secure.get(SecureKeys.LLM_API_KEY)).isNull()
    }
}

private class InMemorySecureStringStore : SecureStringStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = map[key]
    override suspend fun put(key: String, value: String) {
        map[key] = value
    }
    override suspend fun remove(key: String) {
        map.remove(key)
    }
}
