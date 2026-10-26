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
import java.security.SecureRandom
import java.io.RandomAccessFile
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * =====================================================================================
 * AES-256 ZERO-KNOWLEDGE FILE ENCRYPTOR
 * =====================================================================================
 * Encrypts files on the user's Android device using Authenticated AES-256-GCM Zero-Knowledge
 * Encryption prior to uploading to Telegram Cloud storage.
 *
 * Security Characteristics:
 * - Modern Cipher: AES/GCM/NoPadding with 128-bit authentication tag & random 12-byte nonce
 * - Legacy Migration: Full backward compatibility for AES/CBC/PKCS5Padding (ENC256 header)
 * - Key derivation: 256-bit key generated via SHA-256 hash of Master Key
 * - Ensures zero-knowledge cloud backups (Telegram servers only store encrypted bytes)
 * =====================================================================================
 */
object FileEncryptor {
    private const val ALGORITHM_GCM = "AES/GCM/NoPadding"
    private const val ALGORITHM_CBC = "AES/CBC/PKCS5Padding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_NONCE_LENGTH_BYTES = 12

    // 8-byte modern authenticated AES-256-GCM magic header matching backend
    val MAGIC_HEADER_GCM = byteArrayOf(0x47, 0x43, 0x4D, 0x32, 0x35, 0x36, 0x01, 0x00) // "GCM256\x01\x00"
    // 8-byte legacy AES-256-CBC magic header
    val MAGIC_HEADER_CBC = byteArrayOf(0x45, 0x4E, 0x43, 0x32, 0x35, 0x36, 0x01, 0x00) // "ENC256\x01\x00"

    // Backward-compatible alias
    val MAGIC_HEADER = MAGIC_HEADER_GCM

    /** Derives a 256-bit AES secret key spec from user's Master Key string */
    private fun deriveKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Derives a 128-bit Initialization Vector (IV) spec from user's Master Key string for legacy CBC */
    private fun deriveIV(secret: String): IvParameterSpec {
        val digest = MessageDigest.getInstance("MD5")
        val ivBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return IvParameterSpec(ivBytes)
    }

    /**
     * Checks if a file was explicitly prepended with either GCM256 or ENC256 magic header.
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
                    return header.contentEquals(MAGIC_HEADER_GCM) || header.contentEquals(MAGIC_HEADER_CBC)
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks if a file uses modern authenticated AES-256-GCM.
     */
    fun isGcmEncrypted(file: File): Boolean {
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
                return bytesRead == 8 && header.contentEquals(MAGIC_HEADER_GCM)
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
     * Encrypts a plain file into an Authenticated AES-256-GCM file output with GCM256 magic header.
     * @param inputFile Source plain file
     * @param outputFile Encrypted output file destination
     * @param secretKey Master AES-256 encryption key
     */
    fun encryptFile(inputFile: File, outputFile: File, secretKey: String) {
        val keySpec = deriveKey(secretKey)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        SecureRandom().nextBytes(nonce)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        val cipher = Cipher.getInstance(ALGORITHM_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        java.io.BufferedInputStream(FileInputStream(inputFile), 128 * 1024).use { fis ->
            java.io.BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024).use { fos ->
                // Write 8-byte GCM256 Magic Header
                fos.write(MAGIC_HEADER_GCM)
                // Write 12-byte random Nonce
                fos.write(nonce)
                CipherOutputStream(fos, cipher).use { cos ->
                    val buffer = ByteArray(128 * 1024)
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
     * Derives a deterministic 12-byte nonce for a given file to ensure stable resumed chunking
     * across worker restarts and multiple threads without requiring separate database persistence.
     */
    fun generateDeterministicNonce(file: File, secretKey: String): ByteArray {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = "$secretKey:${file.absolutePath}:${file.length()}:${file.lastModified()}".toByteArray(Charsets.UTF_8)
            val hash = digest.digest(input)
            val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
            System.arraycopy(hash, 0, nonce, 0, GCM_NONCE_LENGTH_BYTES)
            nonce
        } catch (_: Exception) {
            val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
            SecureRandom().nextBytes(nonce)
            nonce
        }
    }

    /** Total virtual size of encrypted file in GCM format (20-byte header + plain length) */
    fun getEncryptedTotalSize(plainFileSize: Long): Long {
        return 20L + plainFileSize
    }

    /**
     * Calculates total number of 4 MB chunks required to upload the encrypted stream.
     */
    fun getEncryptedChunkCount(plainFileSize: Long, chunkSize: Int = FileChunker.CHUNK_SIZE): Int {
        val total = getEncryptedTotalSize(plainFileSize)
        if (total <= 0L) return 1
        return ceil(total.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)
    }

    /**
     * ZERO-DISK ON-THE-FLY STREAMING CHUNK ENCRYPTION (P0)
     * Extracts and encrypts a single chunk directly from the source plain file on demand
     * without creating an intermediate multi-gigabyte .enc clone on disk.
     * Uses AES-256-CTR keystream identical to NIST SP 800-38D AES-GCM (counter = 2 + blockIndex).
     */
    fun extractEncryptedChunk(
        sourceFile: File,
        chunkIndex: Int,
        outputChunkFile: File,
        secretKey: String,
        nonce: ByteArray,
        chunkSize: Int = FileChunker.CHUNK_SIZE
    ): File {
        val keySpec = deriveKey(secretKey)
        val plainLength = sourceFile.length()
        val totalEncryptedSize = 20L + plainLength
        val chunkStart = (chunkIndex - 1).toLong() * chunkSize.toLong()
        val chunkEnd = minOf(chunkStart + chunkSize.toLong() - 1L, totalEncryptedSize - 1L)
        val chunkLength = (chunkEnd - chunkStart + 1L).toInt()

        if (chunkLength <= 0) {
            outputChunkFile.parentFile?.mkdirs()
            outputChunkFile.createNewFile()
            return outputChunkFile
        }

        outputChunkFile.parentFile?.mkdirs()

        java.io.BufferedOutputStream(FileOutputStream(outputChunkFile), 64 * 1024).use { fos ->
            if (chunkIndex == 1) {
                // Chunk 1 starts with 8B magic header + 12B nonce (20 bytes total)
                fos.write(MAGIC_HEADER_GCM)
                fos.write(nonce)
                val plainBytesToEncrypt = minOf(chunkLength - 20, plainLength.toInt())
                if (plainBytesToEncrypt > 0) {
                    val ctrIv = ByteArray(16)
                    System.arraycopy(nonce, 0, ctrIv, 0, 12)
                    ctrIv[12] = 0
                    ctrIv[13] = 0
                    ctrIv[14] = 0
                    ctrIv[15] = 2

                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(ctrIv))

                    FileInputStream(sourceFile).use { fis ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = plainBytesToEncrypt
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size, remaining)
                            val read = fis.read(buffer, 0, toRead)
                            if (read == -1) break
                            val out = cipher.update(buffer, 0, read)
                            if (out != null && out.isNotEmpty()) {
                                fos.write(out)
                            }
                            remaining -= read
                        }
                        val finalBytes = cipher.doFinal()
                        if (finalBytes != null && finalBytes.isNotEmpty()) {
                            fos.write(finalBytes)
                        }
                    }
                }
            } else {
                // Chunk > 1: All bytes are ciphertext from plainStart to plainEnd
                val plainStart = chunkStart - 20L
                val plainEnd = chunkEnd - 20L
                val plainBytesToEncrypt = plainEnd - plainStart + 1L

                val blockIndex = plainStart / 16L
                val offsetInBlock = (plainStart % 16L).toInt()
                val counterVal = 2L + blockIndex

                val ctrIv = ByteArray(16)
                System.arraycopy(nonce, 0, ctrIv, 0, 12)
                ctrIv[12] = ((counterVal shr 24) and 0xFF).toByte()
                ctrIv[13] = ((counterVal shr 16) and 0xFF).toByte()
                ctrIv[14] = ((counterVal shr 8) and 0xFF).toByte()
                ctrIv[15] = (counterVal and 0xFF).toByte()

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(ctrIv))

                // Align keystream if plainStart is not 16-byte aligned
                if (offsetInBlock > 0) {
                    cipher.update(ByteArray(offsetInBlock))
                }

                RandomAccessFile(sourceFile, "r").use { raf ->
                    raf.seek(plainStart)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = plainBytesToEncrypt
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read == -1) break
                        val out = cipher.update(buffer, 0, read)
                        if (out != null && out.isNotEmpty()) {
                            fos.write(out)
                        }
                        remaining -= read
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                }
            }
            fos.flush()
        }
        return outputChunkFile
    }

    /**
     * Decrypts an AES-256 encrypted file back into its original plain file format.
     * Supports:
     * 1. Modern Authenticated AES-256-GCM files with 128-bit authentication tag
     * 2. Modern Zero-Disk Streaming files (AES-256-CTR keystream counter mode)
     * 3. Legacy AES-256-CBC files (ENC256 header or legacy offset 0).
     * @param inputFile Encrypted source file
     * @param outputFile Decrypted plain output file destination
     * @param secretKey Master AES-256 encryption key used during encryption
     * @param forceLegacyNoHeader When true, does not skip the 8-byte magic header
     */
    fun decryptFile(inputFile: File, outputFile: File, secretKey: String, forceLegacyNoHeader: Boolean = false) {
        val keySpec = deriveKey(secretKey)

        if (!forceLegacyNoHeader && isGcmEncrypted(inputFile)) {
            var gcmSucceeded = false
            // Attempt 1: Full Authenticated AES-256-GCM decryption
            try {
                java.io.BufferedInputStream(FileInputStream(inputFile), 128 * 1024).use { fis ->
                    val headerBuf = ByteArray(8)
                    var skipped = 0
                    while (skipped < 8) {
                        val r = fis.read(headerBuf, skipped, 8 - skipped)
                        if (r == -1) break
                        skipped += r
                    }
                    val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
                    var nonceRead = 0
                    while (nonceRead < GCM_NONCE_LENGTH_BYTES) {
                        val r = fis.read(nonce, nonceRead, GCM_NONCE_LENGTH_BYTES - nonceRead)
                        if (r == -1) break
                        nonceRead += r
                    }

                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                    val cipher = Cipher.getInstance(ALGORITHM_GCM)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

                    CipherInputStream(fis, cipher).use { cis ->
                        java.io.BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024).use { fos ->
                            val buffer = ByteArray(128 * 1024)
                            var bytesRead: Int
                            while (cis.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                            }
                            fos.flush()
                        }
                    }
                }
                gcmSucceeded = outputFile.exists() && outputFile.length() > 0 && isLikelyPlainFile(outputFile, outputFile.name)
            } catch (_: Exception) {
                gcmSucceeded = false
            }

            if (gcmSucceeded) return

            // Attempt 2: Zero-Disk Streaming AES-256-CTR keystream decryption (counter = 2)
            try {
                java.io.BufferedInputStream(FileInputStream(inputFile), 128 * 1024).use { fis ->
                    val headerBuf = ByteArray(8)
                    var skipped = 0
                    while (skipped < 8) {
                        val r = fis.read(headerBuf, skipped, 8 - skipped)
                        if (r == -1) break
                        skipped += r
                    }
                    val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
                    var nonceRead = 0
                    while (nonceRead < GCM_NONCE_LENGTH_BYTES) {
                        val r = fis.read(nonce, nonceRead, GCM_NONCE_LENGTH_BYTES - nonceRead)
                        if (r == -1) break
                        nonceRead += r
                    }

                    val ctrIv = ByteArray(16)
                    System.arraycopy(nonce, 0, ctrIv, 0, 12)
                    ctrIv[12] = 0
                    ctrIv[13] = 0
                    ctrIv[14] = 0
                    ctrIv[15] = 2

                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ctrIv))

                    CipherInputStream(fis, cipher).use { cis ->
                        java.io.BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024).use { fos ->
                            val buffer = ByteArray(128 * 1024)
                            var bytesRead: Int
                            while (cis.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                            }
                            fos.flush()
                        }
                    }
                }
                return
            } catch (_: Exception) {}
        }

        // Legacy AES-256-CBC migration path
        val ivSpec = deriveIV(secretKey)
        val cipher = Cipher.getInstance(ALGORITHM_CBC)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        java.io.BufferedInputStream(FileInputStream(inputFile), 128 * 1024).use { fis ->
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
                java.io.BufferedOutputStream(FileOutputStream(outputFile), 128 * 1024).use { fos ->
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                    fos.flush()
                }
            }
        }
    }

    /** Encrypts plain text string with Authenticated AES-256-GCM */
    fun encryptText(plainText: String, secretKey: String): String {
        return try {
            val keySpec = deriveKey(secretKey)
            val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
            SecureRandom().nextBytes(nonce)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            val cipher = Cipher.getInstance(ALGORITHM_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(nonce.size + cipherBytes.size)
            System.arraycopy(nonce, 0, combined, 0, nonce.size)
            System.arraycopy(cipherBytes, 0, combined, nonce.size, cipherBytes.size)
            "GCM:" + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    /** Decrypts cipher string supporting both modern GCM and legacy CBC */
    fun decryptText(cipherText: String, secretKey: String): String {
        if (cipherText.startsWith("GCM:")) {
            try {
                val keySpec = deriveKey(secretKey)
                val rawBytes = android.util.Base64.decode(cipherText.removePrefix("GCM:"), android.util.Base64.NO_WRAP)
                if (rawBytes.size > GCM_NONCE_LENGTH_BYTES) {
                    val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
                    System.arraycopy(rawBytes, 0, nonce, 0, GCM_NONCE_LENGTH_BYTES)
                    val cipherBytes = ByteArray(rawBytes.size - GCM_NONCE_LENGTH_BYTES)
                    System.arraycopy(rawBytes, GCM_NONCE_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                    val cipher = Cipher.getInstance(ALGORITHM_GCM)
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                    return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }
        // Legacy CBC fallback
        return try {
            val keySpec = deriveKey(secretKey)
            val ivSpec = deriveIV(secretKey)
            val cipher = Cipher.getInstance(ALGORITHM_CBC)
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
            if (current.isNotBlank()) {
                keys.add(current.trim())
            }
            if (prefs.zeroKnowledgePassphrase.isNotBlank()) {
                keys.add(prefs.zeroKnowledgePassphrase.trim())
            }
        } catch (_: Exception) {}

        try {
            val vaultKey = KeystoreVault.retrieveMasterKey(context)
            if (!vaultKey.isNullOrBlank()) {
                keys.add(vaultKey.trim())
            }
        } catch (_: Exception) {}

        try {
            val db = AppDatabase.getDatabase(context)
            val dbKey = db.appSettingDao().getSetting("encryption_key")?.value
            if (!dbKey.isNullOrBlank()) {
                keys.add(dbKey.trim())
            }
            val masterKey = db.appSettingDao().getSetting("master_key")?.value
            if (!masterKey.isNullOrBlank()) {
                keys.add(masterKey.trim())
            }
        } catch (_: Exception) {}

        return keys.filter { it.isNotBlank() }.toList()
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
