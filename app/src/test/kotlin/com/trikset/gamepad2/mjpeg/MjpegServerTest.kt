package com.trikset.gamepad2.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.video.MjpegVideoPlayer
import java.io.IOException
import java.net.URL
import org.apache.commons.io.input.BoundedInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end MJPEG decode tests against a real HTTP [SyntheticMjpegServer], streaming the committed
 * CC0 cat fixtures (`mjpeg/vintage_cat_9405680_*.jpg`, license in the same resources dir). Proves
 * the real HTTP + multipart + decode pipeline renders arbitrary-size photographic frames (not
 * synthetic solids), and covers the server's drop/reconnect behavior. Runs under
 * [GraphicsMode.Mode.NATIVE] so [BitmapFactory] does **real** JPEG decoding. The app's own
 * [MjpegVideoPlayer.openStream] is the client.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MjpegServerTest : RobolectricTestBase() {

  /** The three committed fixtures at 4:3 / 8:5 / 5:3 (the renderer center-crops any of them). */
  private val fixtures = listOf("640x480", "320x200", "1000x600")

  @Test
  fun eachCatFixtureShouldStreamAndDecodeByteIdenticalAtNativeSize() {
    for (size in fixtures) {
      val expected = SyntheticMjpegServer.catFrameImage(size)
      withServer(SyntheticMjpegServer(frameImages = listOf(expected), framesPerConnection = 10)) {
          port ->
        withStream(url(port)) { stream ->
          // The parser drops frames when the socket buffer holds >2x a frame (available() gate),
          // so loop until a frame survives (same as the old SyntheticMjpegServerTest).
          val frame = readFirstFrame(stream)
          assertTrue("frame must be non-null for $size", frame != null)
          val bytes = frame!!.readBytes()
          frame.close()
          assertTrue(
              "decoded bytes must equal the seeded fixture ($size)",
              expected.contentEquals(bytes),
          )
          // The fixture must be a real photo: decodes at native size and is multi-color.
          val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          assertEquals(
              "$size must decode at its native size",
              size.split("x")[0].toInt(),
              bmp.width,
          )
          assertEquals(
              "$size must decode at its native size",
              size.split("x")[1].toInt(),
              bmp.height,
          )
          assertTrue("$size must be a multi-color frame", distinctColors(bmp) >= 8)
          bmp.recycle()
        }
      }
    }
  }

  @Test
  fun serverShouldCycleThroughDistinctFrames() {
    // Comparable fixture sizes (26KB vs 44KB) so the 2x gate does not systematically drop the
    // smaller one; 50ms pacing lets the client drain the socket between frames.
    val seeded =
        listOf(
            SyntheticMjpegServer.catFrameImage("640x480"),
            SyntheticMjpegServer.catFrameImage("1000x600"),
        )
    withServer(
        SyntheticMjpegServer(frameImages = seeded, framesPerConnection = 10, frameIntervalMs = 50)
    ) { port ->
      withStream(url(port)) { stream ->
        val decoded = readFrames(stream, expected = 4)
        decoded.forEach { bytes -> assertTrue(seeded.any { it.contentEquals(bytes) }) }
        // At least two distinct seeded frames must appear: proves the cycle advanced.
        assertTrue(
            "expected >=2 distinct seeded frames, got ${decoded.distinct().size}",
            decoded.distinct().size >= 2,
        )
      }
    }
  }

  @Test
  fun droppedConnectionShouldThrowThenReconnectAndDecode() {
    val server = SyntheticMjpegServer(framesPerConnection = 4)
    withServer(server) { port ->
      val url = url(port)
      // Server closes abruptly after 4 frames -> the parser must surface an
      // IOException (drop detected), the precondition for R12 reconnect.
      var sawDrop = false
      withStream(url) { stream ->
        val deadline = System.currentTimeMillis() + 15000
        try {
          while (System.currentTimeMillis() < deadline) {
            readFrames(stream, expected = 1)
          }
        } catch (_: IOException) {
          sawDrop = true
        }
      }
      assertTrue("connection drop must surface as IOException", sawDrop)

      // Restore: the app's reconnect path (MjpegVideoPlayer.openStream, same flow
      // as restartVideoStream) re-opens and decodes again.
      withStream(url) { stream ->
        val restored = readFrames(stream, expected = 1)
        assertTrue(restored[0].isNotEmpty())
      }
      assertTrue(server.acceptedConnections.get() >= 2)
    }
  }

  /** Starts [server], runs [block] with the bound port, then always stops it. */
  private fun <T> withServer(server: SyntheticMjpegServer, block: (Int) -> T): T {
    val port = server.start()
    return try {
      block(port)
    } finally {
      server.stop()
    }
  }

  /** Opens a stream to [url], runs [block], then always closes it. */
  private fun <T> withStream(url: URL, block: (MjpegInputStream) -> T): T {
    val stream = openStream(url)
    return try {
      block(stream)
    } finally {
      stream.close()
    }
  }

  private fun url(port: Int): URL = URL("http://127.0.0.1:$port/?action=stream")

  /** Reads until [expected] non-null frames are parsed, returning their raw bytes. */
  private fun readFrames(stream: MjpegInputStream, expected: Int): List<ByteArray> {
    val out = mutableListOf<ByteArray>()
    while (out.size < expected) {
      val frame = readFirstFrame(stream) ?: break
      val bytes = frame.readBytes()
      frame.close()
      assertTrue("frame must carry the seeded JPEG bytes", bytes.isNotEmpty())
      out.add(bytes)
    }
    assertTrue("expected $expected frames, got ${out.size}", out.size >= expected)
    return out
  }

  /** Reads until a non-null frame is parsed (frames may be dropped by the 2x gate). */
  private fun readFirstFrame(stream: MjpegInputStream): BoundedInputStream? {
    val deadline = System.currentTimeMillis() + 15000
    while (System.currentTimeMillis() < deadline) {
      val frame = stream.readMjpegFrame()
      if (frame != null) return frame
    }
    return null
  }

  private fun distinctColors(bmp: Bitmap): Int {
    val seen = mutableSetOf<Int>()
    for (y in 0 until bmp.height step 4) {
      for (x in 0 until bmp.width step 4) {
        seen.add(bmp.getPixel(x, y) and 0xFFFFFF)
        if (seen.size >= 8) return seen.size
      }
    }
    return seen.size
  }

  private fun openStream(url: URL): MjpegInputStream {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    return requireNotNull(MjpegVideoPlayer(view).openStream(url.toString()))
  }
}
