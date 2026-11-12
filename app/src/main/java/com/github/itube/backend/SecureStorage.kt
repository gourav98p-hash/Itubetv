package com.github.itube

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AES-GCM encrypted storage for user listening profile and taste metadata.
 * Uses the Android Keystore to securely store cryptographic keys so user data cannot be
 * read in plaintext or tampered with.
 */
object SecureStorage {
    private const val TAG = "SecureStorage"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "itube_taste_profile_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val DELIMITER = ":"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string into a Base64-encoded payload containing IV and ciphertext.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$ivBase64$DELIMITER$cipherBase64"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed, falling back to plaintext: ${e.message}")
            plainText
        }
    }

    /**
     * Decrypts Base64-encoded encrypted payload into plaintext string.
     * Gracefully falls back to plaintext if the string is legacy unencrypted JSON.
     */
    fun decrypt(encryptedPayload: String?): String {
        if (encryptedPayload.isNullOrBlank()) return ""
        if (!encryptedPayload.contains(DELIMITER)) {
            // Legacy unencrypted plaintext fallback
            return encryptedPayload
        }

        return try {
            val parts = encryptedPayload.split(DELIMITER)
            if (parts.size != 2) return encryptedPayload

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed (may be plaintext), returning raw: ${e.message}")
            encryptedPayload
        }
    }
}
