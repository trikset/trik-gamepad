package com.trikset.gamepad2.mjpeg

import java.io.InputStream
import org.junit.Assert.assertTrue

/**
 * Shared frame-decode assertions for the raw-socket and https stream tests (both decode a live
 * MJPEG stream from a local test server and check the frames match the seeded fixtures). jscpd is
 * 0.0%-strict, so the loop + byte-identity check must not be duplicated per test.
 */
fun assertDecodedFramesMatchSeeded(
    stream: InputStream,
    seeded: List<ByteArray>,
    minFrames: Int = 2,
    deadlineMs: Long = 15000,
) {
  val parser = MjpegInputStream(stream)
  val decoded = mutableListOf<ByteArray>()
  val deadline = System.currentTimeMillis() + deadlineMs
  while (decoded.size < minFrames && System.currentTimeMillis() < deadline) {
    val frame = parser.readMjpegFrame() ?: continue
    decoded.add(frame.readBytes())
    frame.close()
  }
  assertTrue("expected >=$minFrames frames, got ${decoded.size}", decoded.size >= minFrames)
  val byteMatch = decoded.any { bytes -> seeded.any { it.contentEquals(bytes) } }
  assertTrue("expected a frame byte-identical to a seeded JPEG", byteMatch)
}
