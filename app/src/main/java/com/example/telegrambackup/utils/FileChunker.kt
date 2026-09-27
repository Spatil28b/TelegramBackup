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
        var bytesInCurrentChunk = 0

        FileInputStream(sourceFile).use { inputStream ->
            var currentChunkFile = File(outputDir, "${sourceFile.name}.part%03d".format(chunkIndex))
            var outputStream = FileOutputStream(currentChunkFile)
            chunkFiles.add(currentChunkFile)

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                var offset = 0
                while (offset < bytesRead) {
                    val remainingInChunk = chunkSize - bytesInCurrentChunk
                    val bytesToWrite = minOf(remainingInChunk, bytesRead - offset)
                    outputStream.write(buffer, offset, bytesToWrite)
                    bytesInCurrentChunk += bytesToWrite
                    offset += bytesToWrite

                    if (bytesInCurrentChunk >= chunkSize) {
                        outputStream.close()
                        chunkIndex++
                        bytesInCurrentChunk = 0
                        currentChunkFile = File(outputDir, "${sourceFile.name}.part%03d".format(chunkIndex))
                        outputStream = FileOutputStream(currentChunkFile)
                        chunkFiles.add(currentChunkFile)
                    }
                }
            }
            outputStream.close()
            if (currentChunkFile.length() == 0L && chunkFiles.size > 1) {
                chunkFiles.remove(currentChunkFile)
                currentChunkFile.delete()
            }
        }

        return chunkFiles
    }

    /**
     * Extracts a single chunk on demand without loading the full file into memory.
     * Supports dynamic chunk size (1 MB, 2 MB, 4 MB).
     */
    fun extractSingleChunk(sourceFile: File, chunkIndex: Int, outputFile: File, chunkSize: Int = CHUNK_SIZE): File {
        val offset = (chunkIndex - 1).toLong() * chunkSize.toLong()
        val toTransfer = minOf(chunkSize.toLong(), maxOf(0L, sourceFile.length() - offset))
        RandomAccessFile(sourceFile, "r").use { raf ->
            val inChannel = raf.channel
            FileOutputStream(outputFile).use { fos ->
                val outChannel = fos.channel
                var position = offset
                var remaining = toTransfer
                while (remaining > 0) {
                    val transferred = inChannel.transferTo(position, remaining, outChannel)
                    if (transferred <= 0) break
                    position += transferred
                    remaining -= transferred
                }
            }
        }
        return outputFile
    }

    /**
     * Merges sequential chunk files back into a single restored file.
     * Uses zero-copy OS kernel FileChannel DMA transfers for maximum flash throughput.
     */
    fun mergeChunks(chunkFiles: List<File>, outputFile: File) {
        val chunkRegex = Regex("""\.part(\d+)$""")
        val sorted = chunkFiles.sortedWith(Comparator { a, b ->
            val idxA = chunkRegex.find(a.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val idxB = chunkRegex.find(b.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            idxA.compareTo(idxB)
        })
        FileOutputStream(outputFile).use { fos ->
            val outChannel = fos.channel
            for (chunk in sorted) {
                FileInputStream(chunk).use { fis ->
                    val inChannel = fis.channel
                    var position = 0L
                    val size = inChannel.size()
                    while (position < size) {
                        val transferred = inChannel.transferTo(position, size - position, outChannel)
                        if (transferred <= 0) break
                        position += transferred
                    }
                }
            }
        }
    }
}
