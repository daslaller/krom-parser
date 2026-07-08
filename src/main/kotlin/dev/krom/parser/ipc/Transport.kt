package dev.krom.parser.ipc

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Stdio transport with Content-Length header framing.
 * Same wire format as LSP — any tool that speaks LSP can debug this.
 */
class Transport(
    private val input: InputStream,
    private val output: OutputStream,
) {
    private val buffer = StringBuilder()

    /**
     * Reads one complete message from stdin. Blocks until a full message is available.
     * Returns null on EOF.
     */
    fun read(): String? {
        // Read headers until we find Content-Length
        val contentLength = readHeaders() ?: return null

        // Read exactly contentLength bytes
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(body, read, contentLength - read)
            if (n == -1) return null
            read += n
        }

        return String(body, StandardCharsets.UTF_8)
    }

    /**
     * Writes a message to stdout with Content-Length header framing.
     */
    fun write(message: String) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n"
        synchronized(output) {
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.write(body)
            output.flush()
        }
    }

    private fun readHeaders(): Int? {
        var contentLength = -1
        val line = StringBuilder()

        while (true) {
            val b = input.read()
            if (b == -1) return null

            val c = b.toChar()
            line.append(c)

            // Check for \r\n
            if (line.endsWith("\r\n")) {
                val headerLine = line.toString().trimEnd()
                if (headerLine.isEmpty()) {
                    // Empty line = end of headers
                    if (contentLength < 0) return null
                    return contentLength
                }
                if (headerLine.lowercase().startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: -1
                }
                line.clear()
            }
        }
    }
}
