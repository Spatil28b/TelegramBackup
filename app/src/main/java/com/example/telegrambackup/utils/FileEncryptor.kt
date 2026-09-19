package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AppSettingEntity
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
    // 8-byte magic header identifying AES-256 encrypted payload
    val MAGIC_HEADER = byteArrayOf(0x45, 0x4E, 0x43, 0x32, 0x35, 0x36, 0x01, 0x00) // "ENC256\x01\x00"

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
     * Checks if a file was explicitly prepended with the 8-byte ENC256 magic header.
     */
    fun isEncryptedWithMagicHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8)
                var bytesRead = 0
                while (bytesRead < 8) {
                    val r = fis.read(header, bytesRead, 8 - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }
                if (bytesRead == 8) {
                    return header.contentEquals(MAGIC_HEADER)
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Inspects the file to determine if it is ALREADY a valid, unencrypted plain file.
     * Uses binary magic bytes for PDF (%PDF), JPEG, PNG, ZIP, WebP, GIF, MP4, MP3, and UTF-8 text.
     */
    fun isLikelyPlainFile(file: File, fileName: String = file.name): Boolean {
        if (!file.exists() || file.length() < 4) return false
        if (isEncryptedWithMagicHeader(file)) return false

        val ext = fileName.substringAfterLast('.', "").lowercase()
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(1024)
                val readCount = fis.read(buffer)
                if (readCount < 4) return false

                when (ext) {
                    "pdf" -> {
                        // PDF magic check: %PDF (0x25, 0x50, 0x44, 0x46) anywhere within the first 1024 bytes
                        for (i in 0..(readCount - 4)) {
                            if (buffer[i] == 0x25.toByte() &&
                                buffer[i + 1] == 0x50.toByte() &&
                                buffer[i + 2] == 0x44.toByte() &&
                                buffer[i + 3] == 0x46.toByte()
                            ) {
                                return true
                            }
                        }
                        return false
                    }
                    "jpg", "jpeg" -> {
                        return readCount >= 3 &&
                                buffer[0] == 0xFF.toByte() &&
                                buffer[1] == 0xD8.toByte() &&
                                buffer[2] == 0xFF.toByte()
                    }
                    "png" -> {
                        return readCount >= 8 &&
                                buffer[0] == 0x89.toByte() &&
                                buffer[1] == 0x50.toByte() &&
                                buffer[2] == 0x4E.toByte() &&
                                buffer[3] == 0x47.toByte()
                    }
                    "zip", "apk", "docx", "xlsx", "pptx", "jar" -> {
                        return readCount >= 4 &&
                                buffer[0] == 0x50.toByte() &&
                                buffer[1] == 0x4B.toByte() &&
                                (buffer[2] == 0x03.toByte() || buffer[2] == 0x05.toByte() || buffer[2] == 0x07.toByte())
                    }
                    "webp" -> {
                        return readCount >= 12 &&
                                buffer[0] == 'R'.code.toByte() && buffer[1] == 'I'.code.toByte() &&
                                buffer[2] == 'F'.code.toByte() && buffer[3] == 'F'.code.toByte() &&
                                buffer[8] == 'W'.code.toByte() && buffer[9] == 'E'.code.toByte() &&
                                buffer[10] == 'B'.code.toByte() && buffer[11] == 'P'.code.toByte()
                    }
                    "gif" -> {
                        return readCount >= 4 &&
                                buffer[0] == 'G'.code.toByte() && buffer[1] == 'I'.code.toByte() &&
                                buffer[2] == 'F'.code.toByte() && buffer[3] == '8'.code.toByte()
                    }
                    "mp4", "mov", "3gp", "m4v" -> {
                        for (i in 4..minOf(readCount - 4, 16)) {
                            if (buffer[i] == 'f'.code.toByte() && buffer[i + 1] == 't'.code.toByte() &&
                                buffer[i + 2] == 'y'.code.toByte() && buffer[i + 3] == 'p'.code.toByte()
                            ) {
                                return true
                            }
                        }
                        return false
                    }
                    "mp3" -> {
                        return (readCount >= 3 && buffer[0] == 'I'.code.toByte() && buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) ||
                               (readCount >= 2 && buffer[0] == 0xFF.toByte() && (buffer[1].toInt() and 0xE0) == 0xE0)
                    }
                    "txt", "json", "csv", "xml", "log", "md", "kt", "java", "py", "html", "css", "js", "yml", "yaml" -> {
                        val inspectLen = minOf(readCount, 512)
                        var nullByteCount = 0
                        for (i in 0 until inspectLen) {
                            if (buffer[i] == 0.toByte()) nullByteCount++
                        }
                        return nullByteCount == 0
                    }
                    else -> {
                        // Unknown extension: If it doesn't have the ENC256 header, treat as plain
                        return !isEncryptedWithMagicHeader(file)
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks if a file is encrypted. Detects both modern files (with ENC256 magic header)
     * and legacy files from earlier versions (raw ciphertext lacking expected binary magic headers).
     */
    fun isEncryptedFile(file: File, fileName: String = file.name): Boolean {
        if (!file.exists() || file.length() < 4) return false
        if (isEncryptedWithMagicHeader(file)) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val knownTypes = setOf("pdf", "jpg", "jpeg", "png", "webp", "gif", "zip", "apk", "docx", "xlsx", "pptx", "jar", "mp4", "mov", "txt", "json")
        if (knownTypes.contains(ext)) {
            return !isLikelyPlainFile(file, fileName)
        }
        return false
    }

    /**
     * Encrypts a plain file into an AES-256 encrypted file output with magic header.
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
                // Write 8-byte Magic Header first
                fos.write(MAGIC_HEADER)
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
     * Supports both modern files (with 8-byte magic header) and legacy files (offset 0).
     * @param inputFile Encrypted source file
     * @param outputFile Decrypted plain output file destination
     * @param secretKey Master AES-256 encryption key used during encryption
     * @param forceLegacyNoHeader When true, does not skip the 8-byte magic header
     */
    fun decryptFile(inputFile: File, outputFile: File, secretKey: String, forceLegacyNoHeader: Boolean = false) {
        val keySpec = deriveKey(secretKey)
        val ivSpec = deriveIV(secretKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        FileInputStream(inputFile).use { fis ->
            if (!forceLegacyNoHeader && isEncryptedWithMagicHeader(inputFile)) {
                // Safely read and discard the 8-byte magic header
                val magicBuf = ByteArray(8)
                var bytesSkipped = 0
                while (bytesSkipped < 8) {
                    val r = fis.read(magicBuf, bytesSkipped, 8 - bytesSkipped)
                    if (r == -1) break
                    bytesSkipped += r
                }
            }

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

    /**
     * Gathers all potential AES keys stored or ever configured on this device
     * to ensure zero-loss recovery of encrypted backups even across app updates.
     * Includes "" (empty string) for early v2.0.0 releases where default key was blank.
     */
    fun getCandidateKeys(context: Context): List<String> {
        val keys = LinkedHashSet<String>()
        try {
            val prefs = AppPrefs(context)
            val current = prefs.encryptionKey
            if (current.isNotEmpty()) {
                keys.add(current)
                keys.add(current.trim())
            }
            if (prefs.zeroKnowledgePassphrase.isNotEmpty()) {
                keys.add(prefs.zeroKnowledgePassphrase)
                keys.add(prefs.zeroKnowledgePassphrase.trim())
            }
            if (prefs.userEmail.isNotEmpty()) keys.add(prefs.userEmail.trim())
            if (prefs.recoveryEmail.isNotEmpty()) keys.add(prefs.recoveryEmail.trim())
            if (prefs.chatId.isNotEmpty()) {
                val cId = prefs.chatId.trim()
                keys.add(cId)
                if (cId.startsWith("-100")) {
                    keys.add(cId.removePrefix("-100"))
                } else {
                    keys.add("-100$cId")
                }
            }
            if (prefs.botToken.isNotEmpty()) keys.add(prefs.botToken.trim())
            if (prefs.displayName.isNotEmpty()) keys.add(prefs.displayName.trim())
        } catch (_: Exception) {}

        try {
            val vaultKey = KeystoreVault.retrieveMasterKey(context)
            if (!vaultKey.isNullOrEmpty()) {
                keys.add(vaultKey)
                keys.add(vaultKey.trim())
            }
        } catch (_: Exception) {}

        try {
            val db = AppDatabase.getDatabase(context)
            val dbKey = db.appSettingDao().getSetting("encryption_key")?.value
            if (!dbKey.isNullOrEmpty()) {
                keys.add(dbKey)
                keys.add(dbKey.trim())
            }
            val masterKey = db.appSettingDao().getSetting("master_key")?.value
            if (!masterKey.isNullOrEmpty()) {
                keys.add(masterKey)
                keys.add(masterKey.trim())
            }
            val allSettings = db.appSettingDao().getAllSettings()
            for (setting in allSettings) {
                val v = setting.value.trim()
                if (v.length in 4..64) {
                    keys.add(v)
                }
            }
            val allBots = db.telegramBotDao().getAllBots()
            for (bot in allBots) {
                val token = bot.botToken.trim()
                if (token.isNotEmpty()) {
                    keys.add(token)
                }
            }
        } catch (_: Exception) {}

        // Known default / legacy fallback keys from earlier releases
        keys.add("") // Crucial for v2.0.0 backups where encryption was enabled but key was blank
        keys.add("default_aes_key")
        keys.add("telegram_backup_master_key")
        keys.add("TelegramBackupKey2025")
        keys.add("AES256-DEFAULT-KEY")
        keys.add("master_key")
        keys.add("backup_key")
        keys.add("password")
        keys.add("123456")

        return keys.toList()
    }

    /**
     * Checks if a decrypted file matches the expected binary format based on its file extension.
     */
    fun verifyDecryptedFormat(file: File, fileName: String): Boolean {
        return isLikelyPlainFile(file, fileName)
    }

    /** Decrypts an encrypted file in place ONLY IF IT IS ACTUALLY ENCRYPTED */
    fun decryptFileInPlace(file: File, secretKey: String): Boolean {
        if (!file.exists()) return false
        if (isLikelyPlainFile(file, file.name)) return true

        val tempDecrypted = File(file.parentFile, "${file.name}.dec_tmp")
        return try {
            decryptFile(file, tempDecrypted, secretKey)
            if (tempDecrypted.exists() && tempDecrypted.length() > 0 && isLikelyPlainFile(tempDecrypted, file.name)) {
                val copied = try {
                    tempDecrypted.copyTo(file, overwrite = true)
                    tempDecrypted.delete()
                    true
                } catch (_: Exception) {
                    file.delete()
                    tempDecrypted.renameTo(file)
                }
                copied && isLikelyPlainFile(file, file.name)
            } else {
                if (tempDecrypted.exists()) tempDecrypted.delete()
                false
            }
        } catch (_: Exception) {
            if (tempDecrypted.exists()) tempDecrypted.delete()
            false
        }
    }

    /**
     * Decrypts an encrypted file in place using candidate keys known to this app instance.
     * When a matching key successfully decrypts the file, the working key is automatically
     * synchronized back to AppPrefs and Room DB so future decryptions are instant.
     * Tests both modern (with magic header) and legacy (offset 0) cipherstreams.
     */
    fun decryptFileInPlaceWithCandidates(file: File, context: Context, originalFileName: String = file.name): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (isLikelyPlainFile(file, originalFileName)) return true

        val candidates = getCandidateKeys(context)
        val hasMagic = isEncryptedWithMagicHeader(file)

        for (candidate in candidates) {
            val tempDecrypted = File(file.parentFile, "${file.name}.cand_dec")

            // Attempt 1: Standard decryption (stripping magic header if present)
            try {
                decryptFile(file, tempDecrypted, candidate, forceLegacyNoHeader = false)
                if (tempDecrypted.exists() && tempDecrypted.length() > 0 && verifyDecryptedFormat(tempDecrypted, originalFileName)) {
                    val copied = try {
                        tempDecrypted.copyTo(file, overwrite = true)
                        tempDecrypted.delete()
                        true
                    } catch (_: Exception) {
                        if (file.delete()) {
                            tempDecrypted.renameTo(file)
                        } else {
                            false
                        }
                    }
                    if (copied && verifyDecryptedFormat(file, originalFileName)) {
                        syncWorkingKey(context, candidate)
                        return true
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (tempDecrypted.exists()) tempDecrypted.delete()
            }

            // Attempt 2: Legacy decryption (offset 0 without skipping header, for older backups)
            if (!hasMagic) {
                try {
                    decryptFile(file, tempDecrypted, candidate, forceLegacyNoHeader = true)
                    if (tempDecrypted.exists() && tempDecrypted.length() > 0 && verifyDecryptedFormat(tempDecrypted, originalFileName)) {
                        val copied = try {
                            tempDecrypted.copyTo(file, overwrite = true)
                            tempDecrypted.delete()
                            true
                        } catch (_: Exception) {
                            if (file.delete()) {
                                tempDecrypted.renameTo(file)
                            } else {
                                false
                            }
                        }
                        if (copied && verifyDecryptedFormat(file, originalFileName)) {
                            syncWorkingKey(context, candidate)
                            return true
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    if (tempDecrypted.exists()) tempDecrypted.delete()
                }
            }
        }
        return false
    }

    private fun syncWorkingKey(context: Context, candidate: String) {
        if (candidate.isNotBlank() && candidate != "default_aes_key") {
            try {
                val prefs = AppPrefs(context)
                prefs.forceSetEncryptionKey(candidate)
                AppDatabase.getDatabase(context).appSettingDao().saveSetting(
                    AppSettingEntity("encryption_key", candidate)
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Universal guard: Ensures a given file is completely decrypted.
     * If the file is already a valid plain file, immediately returns true.
     * Otherwise, attempts multi-key candidate decryption with binary format verification.
     */
    fun ensureDecrypted(file: File, context: Context, fileName: String = file.name): Boolean {
        if (!file.exists()) return false
        if (isLikelyPlainFile(file, fileName)) return true
        return decryptFileInPlaceWithCandidates(file, context, fileName)
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
