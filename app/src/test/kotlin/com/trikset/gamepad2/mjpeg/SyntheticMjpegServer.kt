package com.trikset.gamepad2.mjpeg

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * MJPEG-over-HTTP test server for the unit suite. Binds an **ephemeral** port (each of the 3
 * parallel unit-test JVMs gets its own; or a caller-chosen port via [port], used by the "robot
 * offline on a known address, then back" simulation), serves `multipart/x-mixed-replace` frames
 * from a cycling set of JPEG images, and can **drop the connection after N frames** before resuming
 * the accept loop — emulating a real robot stream dying and coming back (reconnect-on-error).
 *
 * Frames come from the committed CC0 test fixtures (`src/test/resources/mjpeg/`, see
 * [catFrameImages]) — a real vintage-cat photo at three resolutions — not from in-memory generated
 * bitmaps. Decoding them exercises the full JPEG path on the client, so the tests must run under
 * `@GraphicsMode(NATIVE)` for real `BitmapFactory` work.
 */
class SyntheticMjpegServer(
    /** JPEG-encoded frames to cycle; default: the three CC0 cat fixtures. */
    private val frameImages: List<ByteArray> = catFrameImages(),
    /** Frames served per connection before an abrupt close. */
    private val framesPerConnection: Int = 4,
    /** Pause between frames so the client's available()-based parser keeps up. */
    private val frameIntervalMs: Long = 20,
    /** Port to bind, or 0 (default) for an ephemeral port. */
    private val port: Int = 0,
) : Closeable {
  val servedFrames = AtomicInteger(0)
  val acceptedConnections = AtomicInteger(0)

  private var serverSocket: ServerSocket? = null
  private var acceptThread: Thread? = null
  @Volatile private var running = false

  /** Binds and starts accepting; returns the bound port. */
  fun start(): Int {
    serverSocket = ServerSocket(port)
    running = true
    val socket = requireNotNull(serverSocket)
    acceptThread =
        Thread {
              while (running) {
                try {
                  val client = socket.accept()
                  acceptedConnections.incrementAndGet()
                  Thread { serveConnection(client) }.start()
                } catch (_: IOException) {
                  // socket closed on stop(); loop exits below.
                }
              }
            }
            .apply { isDaemon = true }
    acceptThread?.start()
    return socket.localPort
  }

  fun stop() {
    running = false
    try {
      serverSocket?.close()
    } catch (_: IOException) {
      // already closed
    }
    acceptThread?.interrupt()
  }

  override fun close() {
    stop()
  }

  // Client-disconnect is the expected way this loop ends; the exception carries nothing actionable.
  @Suppress("SwallowedException")
  private fun serveConnection(client: Socket) {
    try {
      client.use {
        // Consume the HTTP request headers so the client's write doesn't block.
        HttpRequestHead.read(it.getInputStream())
        val out = BufferedOutputStream(it.getOutputStream())
        writeResponseHeaders(out)
        out.flush()
        for (i in 0 until framesPerConnection) {
          if (!running) return
          val jpeg = frameImages[i % frameImages.size]
          writeFrame(out, jpeg)
          out.flush()
          servedFrames.incrementAndGet()
          if (frameIntervalMs > 0) {
            Thread.sleep(frameIntervalMs)
          }
        }
        // Abrupt drop: close the socket (no trailer) so the client sees EOF/IOException.
      }
    } catch (e: IOException) {
      // Client went away mid-frame; drop the connection as usual.
    } catch (e: InterruptedException) {
      // server stopping
    }
  }

  private fun writeResponseHeaders(out: OutputStream) {
    val headers =
        ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n" +
                "\r\n")
            .toByteArray(Charsets.US_ASCII)
    out.write(headers)
  }

  companion object {
    const val BOUNDARY = "--trikgamepad"
    const val CONTENT_TYPE = "multipart/x-mixed-replace; boundary=$BOUNDARY"

    /**
     * Writes one MJPEG frame (boundary + headers + JPEG) to [out]; shared by the http/https
     * servers.
     */
    fun writeFrame(out: OutputStream, jpeg: ByteArray) {
      val boundary = "$BOUNDARY\r\n".toByteArray(Charsets.US_ASCII)
      val contentLength =
          "Content-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
              .toByteArray(Charsets.US_ASCII)
      out.write(boundary)
      out.write(contentLength)
      out.write(jpeg)
    }

    /** Loads one committed CC0 cat fixture (a "640x480"-style size token) from the classpath. */
    fun catFrameImage(size: String): ByteArray {
      val path = "/mjpeg/vintage_cat_9405680_${size}.jpg"
      val stream =
          requireNotNull(SyntheticMjpegServer::class.java.getResourceAsStream(path)) {
            "missing test fixture $path"
          }
      return stream.use { it.readBytes() }
    }

    /**
     * The three committed CC0 cat fixtures (640x480 / 320x200 / 1000x600, license in the same
     * resources dir): real photographic frames at three aspect ratios, so a stream can be any
     * size/proportion. The 640x480 and 1000x600 sizes are comparable enough for the parser's
     * available() 2x gate to keep the smaller frames; tests that cycle must use comparable sizes.
     */
    fun catFrameImages(): List<ByteArray> =
        listOf(catFrameImage("640x480"), catFrameImage("320x200"), catFrameImage("1000x600"))
  }
}
