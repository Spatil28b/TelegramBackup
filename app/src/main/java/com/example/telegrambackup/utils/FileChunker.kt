package com.example.telegrambackup.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.ceil

/**
 * =====================================================================================
 * LARGE FILE CHUNKER UTILITY (ADAPTIVE MICRO-CHUNK ENGINE)
 * =====================================================================================
 * Splits large files exceeding Telegram Bot API upload limits into ultra-reliable
 * 4 MB, 2 MB, or 1 MB chunks for rapid transmission and zero-data-loss resume capability.
 * =====================================================================================
 */
object FileChunker {
    /** Default 4 MB chunk size threshold for maximum network stability and fast resumability */
    const val CHUNK_SIZE = 4 * 1024 * 1024
    const val MICRO_CHUNK_SIZE_2MB = 2 * 1024 * 1024
    const val MICRO_CHUNK_SIZE_1MB = 1 * 1024 * 1024

    /**
     * Checks if a file exceeds the default chunk threshold.
     */
    fun isLargeFile(file: File, chunkSize: Int = CHUNK_SIZE): Boolean {
        return file.length() > chunkSize
    }

    /**
     * Calculates total number of chunks required to split the target file.
     */
    fun getChunkCount(file: File, chunkSize: Int = CHUNK_SIZE): Int {
        if (file.length() <= 0) return 1
        return ceil(file.length().toDouble() / chunkSize.toDouble()).toInt()
    }

    /**
     * Splits a large file into sequential chunk files (`.part001`, `.part002`, etc.).
     */
    fun splitFile(sourceFile: File, outputDir: File, chunkSize: Int = CHUNK_SIZE): List<File> {
        val chunkFiles = mutableListOf<File>()
        val buffer = ByteArray(8192)
        var chunkIndex = 1
        var bytesInCurrentChunk = 0L

        FileInputStream(sourceFile).use { inputStream ->
            var currentChunkFile = File(outputDir, "${sourceFile.name}.part%03d".format(chunkIndex))
            var outputStream = FileOutputStream(currentChunkFile)
            chunkFiles.add(currentChunkFile)

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (bytesInCurrentChunk + bytesRead > chunkSize) {
                    outputStream.close()
                    chunkIndex++
                    bytesInCurrentChunk = 0L
                    currentChunkFile = File(outputDir, "${sourceFile.name}.part%03d".format(chunkIndex))
                    outputStream = FileOutputStream(currentChunkFile)
                    chunkFiles.add(currentChunkFile)
                }

                outputStream.write(buffer, 0, bytesRead)
                bytesInCurrentChunk += bytesRead
            }
            outputStream.close()
        }

        return chunkFiles
    }

    /**
     * Extracts a single chunk on demand without loading the full file into memory.
     * Supports dynamic chunk size (1 MB, 2 MB, 4 MB).
     */
    fun extractSingleChunk(sourceFile: File, chunkIndex: Int, outputFile: File, chunkSize: Int = CHUNK_SIZE): File {
        val offset = (chunkIndex - 1).toLong() * chunkSize.toLong()
        RandomAccessFile(sourceFile, "r").use { raf ->
            raf.seek(offset)
            val buffer = ByteArray(8192)
            var bytesRemaining = chunkSize.toLong()
            FileOutputStream(outputFile).use { fos ->
                while (bytesRemaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    bytesRemaining -= read
                }
            }
        }
        return outputFile
    }

    /**
     * Merges sequential chunk files back into a single restored file.
     */
    fun mergeChunks(chunkFiles: List<File>, outputFile: File) {
        FileOutputStream(outputFile).use { outputStream ->
            val buffer = ByteArray(8192)
            chunkFiles.sortedBy { it.name }.forEach { chunkFile ->
                FileInputStream(chunkFile).use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }
}
