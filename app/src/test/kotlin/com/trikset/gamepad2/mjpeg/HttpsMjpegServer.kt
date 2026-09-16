package com.trikset.gamepad2.mjpeg

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.Closeable
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * MJPEG-over-HTTPS test server for the unit suite: serves the same multipart frames as
 * [SyntheticMjpegServer] but over a **self-signed** TLS endpoint, so the client must trust-all
 * certificates (the robot camera's real-world TLS story). Binds an ephemeral port. The key material
 * is the committed throwaway self-signed cert `https/trik-https-test.p12` (CN=localhost, password
 * `changeit`) — test-only, never used in the app.
 */
class HttpsMjpegServer(
    private val frameImages: List<ByteArray> = SyntheticMjpegServer.catFrameImages(),
) : Closeable {
  private var server: HttpsServer? = null

  /** Binds and starts serving; returns the bound port. */
  fun start(): Int {
    val https =
        HttpsServer.create(InetSocketAddress(0), 0).apply {
          httpsConfigurator = HttpsConfigurator(sslContext())
          createContext("/") { exchange ->
            exchange.responseHeaders.set("Content-Type", SyntheticMjpegServer.CONTENT_TYPE)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
              for (jpeg in frameImages) {
                SyntheticMjpegServer.writeFrame(out, jpeg)
                out.flush()
                Thread.sleep(FRAME_INTERVAL_MS)
              }
            }
          }
        }
    https.start()
    server = https
    return https.address.port
  }

  override fun close() {
    server?.stop(0)
    server = null
  }

  private fun sslContext(): SSLContext {
    val keyStore =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
          val stream =
              requireNotNull(HttpsMjpegServer::class.java.getResourceAsStream(KEYSTORE_RESOURCE)) {
                "missing test keystore $KEYSTORE_RESOURCE"
              }
          stream.use { load(it, KEYSTORE_PASSWORD.toCharArray()) }
        }
    val managers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
          init(keyStore, KEYSTORE_PASSWORD.toCharArray())
        }
    return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
  }

  private companion object {
    const val KEYSTORE_RESOURCE = "/https/trik-https-test.p12"
    const val KEYSTORE_PASSWORD = "changeit"
    const val FRAME_INTERVAL_MS = 20L
  }
}
