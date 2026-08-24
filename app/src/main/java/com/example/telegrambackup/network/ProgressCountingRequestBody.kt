package com.example.telegrambackup.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

/**
 * Custom OkHttp RequestBody wrapper that tracks written bytes in real time
 * and invokes [onProgress] callback with bytesWritten delta and totalBytes.
 * Ensures silky-smooth real-time speed calculation and non-blocking transfer statistics.
 */
class ProgressCountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWrittenDelta: Long, totalBytes: Long, bytesWrittenSoFar: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val totalLength = contentLength()
        var bytesWrittenSoFar = 0L

        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWrittenSoFar += byteCount
                onProgress(byteCount, totalLength, bytesWrittenSoFar)
            }
        }

        val bufferedCountingSink = countingSink.buffer()
        delegate.writeTo(bufferedCountingSink)
        bufferedCountingSink.flush()
    }
}
