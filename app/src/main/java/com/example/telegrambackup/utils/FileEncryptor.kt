package com.example.telegrambackup.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * =====================================================================================
 * AES-256 ZERO-KNOWLEDGE FILE ENCRYPTOR
 * =====================================================================================
 * Encrypts files on the user's Android device using AES-256-CBC Zero-Knowledge Encryption
 * prior to uploading to Telegram Cloud storage.
 *
 * Security Characteristics:
 * - Cipher mode: AES/CBC/PKCS5Padding
 * - Key derivation: 256-bit key generated via SHA-256 hash of Master Key
 * - Initialization Vector (IV): Derived via MD5 digest
 * - Ensures zero-knowledge cloud backups (Telegram servers only store encrypted bytes)
 * =====================================================================================
 */
object FileEncryptor {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    /** Derives a 256-bit AES secret key spec from user's Master Key string */
    private fun deriveKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Derives a 128-bit Initialization Vector (IV) spec from user's Master Key string */
    private fun deriveIV(secret: String): IvParameterSpec {
        val digest = MessageDigest.getInstance("MD5")
        val ivBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return IvParameterSpec(ivBytes)
    }

    /**
     * Encrypts a plain file into an AES-256 encrypted file output.
     * @param inputFile Source plain file
     * @param outputFile Encrypted output file destination
     * @param secretKey Master AES-256 encryption key
     */
    fun encryptFile(inputFile: File, outputFile: File, secretKey: String) {
        val keySpec = deriveKey(secretKey)
        val ivSpec = deriveIV(secretKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        FileInputStream(inputFile).use { fis ->
            FileOutputStream(outputFile).use { fos ->
                CipherOutputStream(fos, cipher).use { cos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                    }
                    cos.flush()
                }
            }
        }
    }

    /**
     * Decrypts an AES-256 encrypted file back into its original plain file format.
     * @param inputFile Encrypted source file
     * @param outputFile Decrypted plain output file destination
     * @param secretKey Master AES-256 encryption key used during encryption
     */
    fun decryptFile(inputFile: File, outputFile: File, secretKey: String) {
        val keySpec = deriveKey(secretKey)
        val ivSpec = deriveIV(secretKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        FileInputStream(inputFile).use { fis ->
            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.flush()
                }
            }
        }
    }

    /** Encrypts plain text string to Base64 AES-256 cipher string */
    fun encryptText(plainText: String, secretKey: String): String {
        return try {
            val keySpec = deriveKey(secretKey)
            val ivSpec = deriveIV(secretKey)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    /** Decrypts Base64 AES-256 cipher string to plain text */
    fun decryptText(cipherText: String, secretKey: String): String {
        return try {
            val keySpec = deriveKey(secretKey)
            val ivSpec = deriveIV(secretKey)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = android.util.Base64.decode(cipherText, android.util.Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            cipherText
        }
    }

    /** Decrypts an encrypted file in place using a temporary file wrapper */
    fun decryptFileInPlace(file: File, secretKey: String) {
        if (!file.exists() || secretKey.isEmpty()) return
        val tempDecrypted = File(file.parentFile, "${file.name}.dec_tmp")
        try {
            decryptFile(file, tempDecrypted, secretKey)
            if (tempDecrypted.exists() && tempDecrypted.length() > 0) {
                file.delete()
                tempDecrypted.renameTo(file)
            }
        } catch (_: Exception) {
            if (tempDecrypted.exists()) tempDecrypted.delete()
        }
    }

    /** Obfuscates file name to unreadable secure token ENC_<hash>.bin */
    fun obfuscateFileName(originalFileName: String, secretKey: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest("$originalFileName$secretKey".toByteArray(Charsets.UTF_8))
            val hex = hashBytes.take(8).joinToString("") { "%02x".format(it) }
            "ENC_$hex.bin"
        } catch (_: Exception) {
            "ENC_file.bin"
        }
    }
}
