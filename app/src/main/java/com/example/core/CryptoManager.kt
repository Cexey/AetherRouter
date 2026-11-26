package com.example.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption manager.
 *
 * All sensitive fields (OpenRouter API keys, message bodies, reasoning traces,
 * reasoning_details blobs, system prompts, and conversation titles) are encrypted
 * at rest with a unique 12-byte random IV per payload.
 */
class CryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aetherrouter_vault_aes256_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val PREFIX = "enc_v1:"
    }

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                cachedKey = existing
                return existing
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            val generated = keyGenerator.generateKey()
            cachedKey = generated
            return generated
        }
    }

    /**
     * Encrypts [plaintext] into an authenticated `enc_v1:<base64(iv + ciphertext)>` string.
     * Empty strings are returned as empty to avoid unnecessary ciphertext bloat on optional fields.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            // Never crash UI on keystore anomalies; surface clearly without leaking key material
            throw IllegalStateException("Vault encryption unavailable", it)
        }
    }

    /**
     * Decrypts a value previously produced by [encrypt].
     * Non-prefixed legacy/unencrypted strings pass through safely without crashing.
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        if (!ciphertext.startsWith(PREFIX)) return ciphertext
        return runCatching {
            val raw = Base64.decode(ciphertext.removePrefix(PREFIX), Base64.NO_WRAP)
            if (raw.size <= IV_LENGTH_BYTES) return ""
            val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
            val encrypted = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
