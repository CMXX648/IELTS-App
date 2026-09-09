package com.voxcoach.core.data.security

import com.google.common.truth.Truth.assertThat
import javax.crypto.KeyGenerator
import org.junit.Test

class AesGcmPayloadTest {
    private fun aesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun sealOpenRoundTrip() {
        val key = aesKey()
        val plain = "sk-test-secret-值".toByteArray(Charsets.UTF_8)
        val packed = AesGcmPayload.seal(key, plain)
        assertThat(packed.size).isGreaterThan(AesGcmPayload.IV_BYTES)
        val opened = AesGcmPayload.open(key, packed)
        assertThat(opened.toString(Charsets.UTF_8)).isEqualTo("sk-test-secret-值")
    }

    @Test
    fun sealProducesDistinctCiphertexts() {
        val key = aesKey()
        val plain = "same".toByteArray(Charsets.UTF_8)
        val a = AesGcmPayload.seal(key, plain)
        val b = AesGcmPayload.seal(key, plain)
        assertThat(a).isNotEqualTo(b)
    }

    @Test(expected = Exception::class)
    fun wrongKeyFailsOpen() {
        val plain = "secret".toByteArray(Charsets.UTF_8)
        val packed = AesGcmPayload.seal(aesKey(), plain)
        AesGcmPayload.open(aesKey(), packed)
    }
}
