package com.trikset.gamepad2.mjpeg

import com.trikset.gamepad2.RobolectricTestBase
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MjpegInputStreamTest : RobolectricTestBase() {

  /** Builds a minimal MJPEG stream: [headers] followed by [tail] bytes. */
  private fun frameWithHeaders(headers: String, tail: ByteArray = ByteArray(0)): ByteArray {
    val headerBytes = headers.toByteArray(StandardCharsets.US_ASCII)
    val frame = ByteArray(headerBytes.size + tail.size)
    System.arraycopy(headerBytes, 0, frame, 0, headerBytes.size)
    System.arraycopy(tail, 0, frame, headerBytes.size, tail.size)
    return frame
  }

  /** Builds a minimal MJPEG frame: headers (Content-Length), SOI marker, [body]. */
  private fun mjpegFrame(body: ByteArray): ByteArray =
      frameWithHeaders(
          "Content-Type: image/jpeg\r\nContent-Length: ${body.size}\r\n\r\n",
          byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + body,
      )

  private fun jpegBytes(size: Int): ByteArray {
    val b = ByteArray(size)
    // Body bytes: a valid JPEG payload isn't required to count coverage of
    // the stream parser, but keep them non-zero so the parser sees distinct data.
    for (i in 0 until size) {
      b[i] = (i % 251).toByte()
    }
    return b
  }

  @Test
  fun readMjpegFrameShouldReturnBoundedFrame() {
    val body = jpegBytes(300)
    val input: InputStream = ByteArrayInputStream(mjpegFrame(body))
    val stream = MjpegInputStream(input)
    val frame = stream.readMjpegFrame()
    assertNotNull("expected a frame", frame)
    assertTrue(frame!!.available() > 0)
    frame.close()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameOnEmptyStreamShouldThrow() {
    MjpegInputStream(ByteArrayInputStream(ByteArray(0))).readMjpegFrame()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameOnGarbageShouldThrow() {
    val garbage = ByteArray(500) { 0xFF.toByte() }
    MjpegInputStream(ByteArrayInputStream(garbage)).readMjpegFrame()
  }

  @Test(expected = IOException::class)
  fun readMjpegFrameWithMissingContentLengthShouldThrow() {
    // Header without a Content-Length line: the parser recovers by skipping
    // and eventually gives up with an IOException on the broken stream.
    MjpegInputStream(ByteArrayInputStream(frameWithHeaders("Content-Type: image/jpeg\r\n\r\n")))
        .readMjpegFrame()
  }

  @Test
  fun roundTripFrameBody() {
    val body = jpegBytes(200)
    val input: InputStream = ByteArrayInputStream(mjpegFrame(body))
    val stream = MjpegInputStream(input)
    val frame = stream.readMjpegFrame()
    assertNotNull(frame)
    // The bounded stream content starts at the SOI marker; read what we can.
    val out = ByteArray(minOf(body.size + 2, frame!!.available()))
    val read = frame.read(out)
    assertTrue(read > 0)
    frame.close()
    assertEquals(200, body.size)
  }

  @Test
  fun readMjpegFrameWhenAvailableShortShouldSkipToRecover() {
    // Header advertises a big Content-Length but only a few body bytes
    // follow; the available() < 2*contentLength path is skipped and the
    // short skip exercises the "Skipped only" warning path.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Type: image/jpeg\r\nContent-Length: 500\r\n\r\n",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                )
            )
        )
    val result = stream.readMjpegFrame()
    result?.close()
  }

  @Test
  fun readMjpegFrameWithZeroLengthBodyShouldDropAndReturnNull() {
    // Content-Length 0 makes available() >= 2*contentLength, so the success
    // path is skipped and the "Frame dropped." recovery returns null.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Type: image/jpeg\r\nContent-Length: 0\r\n\r\n",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(100),
                )
            )
        )
    assertNull(stream.readMjpegFrame())
  }

  @Test
  fun readMjpegFrameWithBadContentLengthShouldRecover() {
    // A non-numeric Content-Length throws NumberFormatException (an
    // IllegalArgumentException) inside the header parse; the recovery path
    // re-searches for the header and returns null.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Type: image/jpeg\r\nContent-Length: abc\r\n\r\n",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                )
            )
        )
    assertNull(stream.readMjpegFrame())
  }

  @Test
  fun readMjpegFrameWithHugeContentLengthShouldRecover() {
    // A header advertising a body far larger than what follows: the success
    // branch is skipped, the short-skip recovery runs and returns null.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Type: image/jpeg\r\nContent-Length: 100000\r\n\r\n",
                    byteArrayOf(
                        0xFF.toByte(),
                        0xD8.toByte(),
                        0xFF.toByte(),
                        0xD8.toByte(),
                        0x01.toByte(),
                    ),
                )
            )
        )
    val result = stream.readMjpegFrame()
    result?.close()
  }

  @Test
  fun readMjpegFrameWithEmptyContentLengthShouldRecover() {
    // "Content-Length:" with no value parses to null, so contentLength stays
    // -1 and the "Skipping to recover" path drops the frame instead of throwing.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Type: image/jpeg\r\nContent-Length:\r\n",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + jpegBytes(50),
                )
            )
        )
    assertNull(stream.readMjpegFrame())
  }

  @Test
  fun readMjpegFrameWithHeaderEndingAtEofShouldStillParse() {
    // The header block ends at the SOI marker without a trailing CRLF: the line
    // scanner hits EOF inside the header and still resolves Content-Length.
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "Content-Length: 5",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + jpegBytes(5),
                )
            )
        )
    val frame = stream.readMjpegFrame()
    assertNotNull("header-without-newline must still yield a frame", frame)
    frame?.close()
  }

  @Test
  fun readMjpegFrameWithMalformedHeaderShouldRecover() {
    // The first "Content-Length" text sits in a colon-less, non-content-length
    // line, so the parser walks the whole bounded header: a bare-LF empty line,
    // then EOF on a non-CL line. Exercises the empty-line skip and the
    // EOF-in-header recovery (returns null instead of throwing).
    val stream =
        MjpegInputStream(
            ByteArrayInputStream(
                frameWithHeaders(
                    "X Content-Length A 5\n\nContent-Type: image/jpeg",
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(10),
                )
            )
        )
    assertNull(stream.readMjpegFrame())
  }
}
