package com.trikset.gamepad2.mjpeg

import com.trikset.gamepad2.diagnostics.AppLog
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.apache.commons.io.input.BoundedInputStream

/**
 * MJPEG-over-HTTP frame parser. Reads the multipart stream headers (a Java `Properties`-style block
 * terminated by an SOI marker) and returns the JPEG body as a bounded stream. On a broken stream it
 * recovers by skipping to the next header, eventually throwing [IOException] when the stream gives
 * up.
 */
class MjpegInputStream(input: InputStream) :
    DataInputStream(BufferedInputStream(input, FRAME_MAX_LENGTH)) {

  companion object {
    private const val TAG = "MjpegInputStream"
    private const val CONTENT_LENGTH = "Content-Length"
    private const val HEADER_MAX_LENGTH = 10000
    private const val FRAME_MAX_LENGTH = 300000 + HEADER_MAX_LENGTH
    private val SOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val CONTENT_LENGTH_MARKER = CONTENT_LENGTH.toByteArray(StandardCharsets.UTF_8)
  }

  @Suppress("SwallowedException") // a truncated read just means "not found"
  private fun getEndOfSequence(sequence: ByteArray): Int {
    mark(FRAME_MAX_LENGTH)
    var result = -1
    try {
      var seqIndex = 0
      for (i in 0 until FRAME_MAX_LENGTH) {
        val c = readUnsignedByte()
        if (c < 0) break
        if (c.toByte() == sequence[seqIndex]) {
          seqIndex++
          if (seqIndex == sequence.size) {
            result = i + 1
            break
          }
        } else {
          seqIndex = 0
        }
      }
    } catch (e: IOException) {
      // Swallowed on purpose: a truncated read just means "not found".
      result = -1
    } finally {
      reset()
    }
    return result
  }

  private fun getStartOfSequence(sequence: ByteArray): Int {
    val end = getEndOfSequence(sequence)
    return if (end < 0) -1 else end - sequence.size
  }

  /**
   * Reads the multipart header block (bounded by the SOI marker) and extracts the `Content-Length`
   * value. Unlike `java.util.Properties.load`, this is a plain CRLF line-scanner faithful to
   * multipart/x-mixed-replace headers (no backslash/whitespace quirks). Returns null when the
   * header has no parseable Content-Length.
   */
  private fun parseContentLength(header: InputStream): Int? {
    lateinit var line: ByteArray
    val lineBuffer = java.io.ByteArrayOutputStream()
    while (true) {
      val b = header.read()
      if (b < 0 || b == '\n'.code) {
        if (lineBuffer.size() > 0) {
          line = lineBuffer.toByteArray()
          lineBuffer.reset()
          val text = String(line, StandardCharsets.UTF_8).trim()
          val separator = text.indexOf(':')
          if (
              separator > 0 &&
                  text.substring(0, separator).equals(CONTENT_LENGTH, ignoreCase = true)
          ) {
            return text.substring(separator + 1).trim().toIntOrNull()
          }
        }
        if (b < 0) {
          return null
        }
      } else {
        lineBuffer.write(b)
      }
    }
  }

  /** @throws IOException if the stream is broken and recovery fails */
  @Throws(IOException::class)
  fun readMjpegFrame(): BoundedInputStream? {
    var contentLength = -1
    val contentAttrPos = getStartOfSequence(CONTENT_LENGTH_MARKER)
    if (contentAttrPos < 0 || skipBytes(contentAttrPos) < contentAttrPos) {
      throw IOException("JPG stream is totally broken or this is extremely huge image")
    }

    try {
      val headerLen = getStartOfSequence(SOI_MARKER)
      val headerIn =
          BoundedInputStream.builder()
              .setInputStream(this)
              .setMaxCount(headerLen.toLong())
              .setPropagateClose(false)
              .get()
      contentLength = parseContentLength(headerIn) ?: -1
      headerIn.close()

      if (contentLength >= 0 && available() < 2 * contentLength) {
        // We must be at the very beginning of data already, but ....
        val skip = getStartOfSequence(SOI_MARKER)
        if (skipBytes(skip) < skip) return null
        return BoundedInputStream.builder()
            .setInputStream(this)
            .setMaxCount(contentLength.toLong())
            .setPropagateClose(false)
            .get()
      }
    } catch (e: IOException) {
      AppLog.d(TAG, "Frame parse recovery", e)
    } catch (e: IllegalArgumentException) {
      AppLog.d(TAG, "Frame parse recovery", e)
    }
    try {
      if (contentLength < 0) {
        AppLog.e(TAG, "Skipping to recover")
        contentLength = getStartOfSequence(CONTENT_LENGTH_MARKER)
      } else {
        AppLog.i(TAG, "Frame dropped.")
      }
      AppLog.v(TAG, "$contentLength bytes to skip until next frame header.")
      val skipped = skipBytes(contentLength)
      if (skipped != contentLength) {
        AppLog.w(TAG, "Skipped only $skipped bytes instead of $contentLength")
      }
    } catch (e: IOException) {
      AppLog.e(TAG, "Failed to skip bad data", e)
    }
    return null
  }
}
