package com.voxcoach.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.voxcoach.core.domain.security.SecureStringStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ST-01 secure apiKey storage: AndroidKeyStore AES-256/GCM wrapping the UTF-8 string.
 * Ciphertext (IV ‖ CT ‖ tag) lives in a private SharedPreferences file — never plaintext.
 * Does **not** use deprecated `security-crypto` EncryptedSharedPreferences (docs/04 §5.1).
 */
@Singleton
class AndroidKeystoreSecureStringStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecureStringStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val packedB64 = prefs.getString(key, null) ?: return@withLock null
            val packed = Base64.decode(packedB64, Base64.NO_WRAP)
            val plain = AesGcmPayload.open(getOrCreateSecretKey(), packed)
            String(plain, Charsets.UTF_8)
        }
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val packed = AesGcmPayload.seal(getOrCreateSecretKey(), value.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
            Unit
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().remove(key).commit()
            Unit
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voxcoach_llm_api_key_aes"
        private const val PREFS_NAME = "voxcoach_secure_strings"
    }
}
