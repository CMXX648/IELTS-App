package com.voxcoach.core.data.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM seal/open for apiKey blobs. IV is prepended to ciphertext.
 * Encrypt lets the provider generate the IV (required by AndroidKeyStore;
 * caller-provided IV is rejected). Key material comes from AndroidKeyStore
 * (production) or a test SecretKey.
 */
internal object AesGcmPayload {
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val IV_BYTES = 12

    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Do not pass GCMParameterSpec on encrypt — AndroidKeyStore forbids caller IV.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv != null && iv.size == IV_BYTES) {
            "unexpected GCM IV length: ${iv?.size}"
        }
        val ct = cipher.doFinal(plain)
        return iv + ct
    }

    fun open(key: SecretKey, packed: ByteArray): ByteArray {
        require(packed.size > IV_BYTES) { "ciphertext too short" }
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ct = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
