package com.trikset.gamepad2

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import org.apache.commons.io.input.BoundedInputStream

/**
 * Opens an HTTP response body over a raw [Socket], bypassing Network Security Config so plain-HTTP
 * MJPEG streams work for any user-configured robot host. Sends the GET request, parses the response
 * head (status line + headers), and exposes a stream positioned at the body, handling
 * `Content-Length`, chunked transfer-encoding, and the connection-until-close case MJPEG servers
 * actually use. Closing the stream closes the socket.
 */
class RawSocketHttpStream
private constructor(
    private val socket: Socket,
    private val body: InputStream,
) : InputStream() {

  override fun read(): Int = body.read()

  override fun read(b: ByteArray, off: Int, len: Int): Int = body.read(b, off, len)

  override fun skip(n: Long): Long = body.skip(n)

  override fun available(): Int = body.available()

  override fun close() {
    try {
      body.close()
    } finally {
      socket.close()
    }
  }

  /** Decodes a `Transfer-Encoding: chunked` body into a plain byte stream. */
  private class ChunkedInputStream(private val input: InputStream) : InputStream() {
    private var remaining = 0L
    private var done = false

    override fun read(): Int {
      val buffer = ByteArray(1)
      val n = read(buffer, 0, 1)
      return if (n < 0) -1 else buffer[0].toUByte().toInt()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (done) return -1
      if (remaining == 0L && !nextChunk()) return -1
      val toRead = minOf(remaining, len.toLong()).toInt()
      val n = input.read(b, off, toRead)
      if (n > 0) {
        remaining -= n
        if (remaining == 0L) {
          // Chunk data is always terminated by CRLF; swallow it before the next size line.
          input.read()
          input.read()
        }
      }
      return n
    }

    private fun nextChunk(): Boolean {
      val sizeLine = readLine() ?: return false
      val size =
          sizeLine.substringBefore(';').trim().toLongOrNull(CHUNK_SIZE_RADIX)
              ?: throw IOException("Bad chunk size: $sizeLine")
      if (size == 0L) {
        done = true
        while (!readLine().isNullOrEmpty()) {
          // Trailer headers; loop until the blank line.
        }
        return false
      }
      remaining = size
      return true
    }

    private fun readLine(): String? {
      val out = ByteArrayOutputStream()
      var b = input.read()
      if (b < 0) return null
      while (b >= 0 && b != '\n'.code) {
        if (b != '\r'.code) out.write(b)
        b = input.read()
      }
      return out.toString(StandardCharsets.US_ASCII.name())
    }
  }

  private data class ResponseHead(
      val statusLine: String,
      val statusCode: Int,
      val headers: Map<String, String>,
  )

  companion object {
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 5000
    private const val MAX_HEAD_SIZE = 8192
    private const val DEFAULT_HTTP_PORT = 80
    private const val CHUNK_SIZE_RADIX = 16
    private const val SUCCESS_STATUS_MIN = 200
    private const val SUCCESS_STATUS_MAX = 299

    /** Opens [url] (http only) over a raw socket and returns a stream of the response body. */
    @Throws(IOException::class)
    fun open(url: URL, socketBinder: SocketBinder = SocketBinder.identity): RawSocketHttpStream {
      val port = if (url.port >= 0) url.port else DEFAULT_HTTP_PORT
      val socket = socketBinder.bind(Socket())
      try {
        socket.connect(InetSocketAddress(url.host, port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS
        socket.tcpNoDelay = true
        val input = BufferedInputStream(socket.getInputStream())
        val path = url.path.ifEmpty { "/" } + (url.query?.let { "?$it" } ?: "")
        val request = "GET $path HTTP/1.1\r\nHost: ${url.host}:$port\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
          write(request.toByteArray(StandardCharsets.US_ASCII))
          flush()
        }
        val head = readResponseHead(input)
        if (
            !head.statusLine.startsWith("HTTP/1.") ||
                head.statusCode !in SUCCESS_STATUS_MIN..SUCCESS_STATUS_MAX
        ) {
          throw IOException("Unexpected HTTP status: ${head.statusLine}")
        }
        val body =
            if (head.headers["transfer-encoding"].equals("chunked", ignoreCase = true)) {
              ChunkedInputStream(input)
            } else {
              val length = head.headers["content-length"]?.toLongOrNull()
              if (length != null) {
                BoundedInputStream.builder().setInputStream(input).setMaxCount(length).get()
              } else {
                input
              }
            }
        return RawSocketHttpStream(socket, body)
      } catch (e: IOException) {
        socket.close()
        throw e
      }
    }

    /** Reads the response head (status + headers) up to the blank-line terminator. */
    private fun readResponseHead(input: InputStream): ResponseHead {
      val out = ByteArrayOutputStream()
      while (out.size() < MAX_HEAD_SIZE) {
        val b = input.read()
        if (b < 0) break
        out.write(b)
        val head = out.toString(StandardCharsets.US_ASCII.name())
        if (head.endsWith("\r\n\r\n") || head.endsWith("\n\n")) break
      }
      return parseHead(out.toString(StandardCharsets.US_ASCII.name()))
    }

    private fun parseHead(head: String): ResponseHead {
      val lines = head.split('\n').map { it.trimEnd('\r') }
      val statusLine = lines.firstOrNull() ?: ""
      val headers = mutableMapOf<String, String>()
      for (line in lines.drop(1)) {
        if (line.isEmpty()) continue
        val separator = line.indexOf(':')
        if (separator > 0) {
          headers[line.substring(0, separator).trim().lowercase()] =
              line.substring(separator + 1).trim()
        }
      }
      val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
      return ResponseHead(statusLine, statusCode, headers)
    }
  }
}
