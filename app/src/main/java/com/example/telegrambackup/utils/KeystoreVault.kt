package com.example.telegrambackup.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * =====================================================================================
 * HARDWARE-BACKED ANDROID KEYSTORE VAULT
 * =====================================================================================
 * Stores the master AES-256 Cloud Drive encryption key inside the device's hardware
 * Trusted Execution Environment (TEE) / Secure Enclave using AndroidKeyStore.
 * =====================================================================================
 */
object KeystoreVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "TelegramBackup_Master_AES_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "keystore_vault_prefs"
    private const val ENCRYPTED_KEY_PREF = "vault_master_key_ciphertext"
    private const val IV_PREF = "vault_master_key_iv"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private val tokenCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun cacheToken(botId: String, token: String) {
        if (botId.isNotBlank() && token.isNotBlank()) {
            tokenCache[botId] = token
        }
    }

    fun getCachedToken(botId: String): String {
        return tokenCache[botId] ?: ""
    }

    /**
     * Encrypts and securely stores an arbitrary string using the hardware-backed master key.
     */
    fun storeEncryptedString(context: Context, keyName: String, value: String): Boolean {
        if (value.isBlank()) {
            deleteEncryptedString(context, keyName)
            return true
        }
        if (keyName.startsWith("bot_token_")) {
            val id = keyName.removePrefix("bot_token_")
            cacheToken(id, value)
        }
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("enc_val_$keyName", Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .putString("enc_iv_$keyName", Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Decrypts and retrieves an arbitrary string stored with storeEncryptedString.
     */
    fun retrieveEncryptedString(context: Context, keyName: String): String? {
        if (keyName.startsWith("bot_token_")) {
            val id = keyName.removePrefix("bot_token_")
            val cached = tokenCache[id]
            if (!cached.isNullOrBlank()) return cached
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cipherTextStr = prefs.getString("enc_val_$keyName", null) ?: return null
            val ivStr = prefs.getString("enc_iv_$keyName", null) ?: return null

            val cipherText = Base64.decode(cipherTextStr, Base64.NO_WRAP)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            val res = String(plainBytes, Charsets.UTF_8)
            if (keyName.startsWith("bot_token_") && res.isNotBlank()) {
                val id = keyName.removePrefix("bot_token_")
                cacheToken(id, res)
            }
            res
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes an encrypted string from the keystore vault preferences.
     */
    fun deleteEncryptedString(context: Context, keyName: String): Boolean {
        if (keyName.startsWith("bot_token_")) {
            val id = keyName.removePrefix("bot_token_")
            tokenCache.remove(id)
        }
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("enc_val_$keyName")
                .remove("enc_iv_$keyName")
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts and saves the raw master AES key into hardware-protected storage.
     */
    fun storeMasterKey(context: Context, rawKey: String): Boolean {
        if (rawKey.isBlank()) return false
        val ok = storeEncryptedString(context, "encryption_key", rawKey)
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(rawKey.toByteArray(Charsets.UTF_8))

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(ENCRYPTED_KEY_PREF, Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .putString(IV_PREF, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        } catch (_: Exception) {}
        return ok
    }

    /**
     * Decrypts and retrieves the raw master AES key from hardware-protected storage.
     */
    fun retrieveMasterKey(context: Context): String? {
        val modernKey = retrieveEncryptedString(context, "encryption_key")
        if (!modernKey.isNullOrBlank()) return modernKey

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cipherTextStr = prefs.getString(ENCRYPTED_KEY_PREF, null) ?: return null
            val ivStr = prefs.getString(IV_PREF, null) ?: return null

            val cipherText = Base64.decode(cipherTextStr, Base64.NO_WRAP)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            val legacyKey = String(plainBytes, Charsets.UTF_8)
            if (legacyKey.isNotBlank()) {
                storeEncryptedString(context, "encryption_key", legacyKey)
            }
            legacyKey
        } catch (_: Exception) {
            null
        }
    }
}

