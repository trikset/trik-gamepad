package com.trikset.gamepad2

import com.trikset.gamepad2.mjpeg.HttpRequestHead
import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import com.trikset.gamepad2.mjpeg.assertDecodedFramesMatchSeeded
import java.io.IOException
import java.net.ServerSocket
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RawSocketHttpStreamTest : RobolectricTestBase() {

  private val seededFrames = SyntheticMjpegServer.catFrameImages()

  @Test
  fun opensStreamFromNonDefaultHostAndReadsFrames() {
    val server =
        SyntheticMjpegServer(
            frameImages = seededFrames,
            framesPerConnection = 10,
            frameIntervalMs = 50,
        )
    try {
      val port = server.start()
      // 127.0.0.1 is NOT the NSC-whitelisted default robot host, so a cleartext
      // HttpURLConnection to it would be blocked on device. The raw socket must
      // not care about NSC.
      val url = URL("http://127.0.0.1:$port/?action=stream")
      val stream = RawSocketHttpStream.open(url)
      try {
        assertDecodedFramesMatchSeeded(stream, seededFrames)
      } finally {
        stream.close()
      }
    } finally {
      server.close()
    }
  }

  @Test
  fun readsChunkedResponseBody() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
            "5\r\nhello\r\n" +
            "6\r\n world\r\n" +
            "0\r\n\r\n",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertEquals('h'.code, stream.read())
        assertEquals("ello world", String(stream.readBytes(), Charsets.US_ASCII))
        // Reading past the terminating 0-chunk exercises the done short-circuit.
        assertEquals(-1, stream.read())
        assertTrue(stream.readBytes().isEmpty())
      }
    }
  }

  @Test
  fun chunkedBodyWithoutTerminatorEndsAtEof() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
            "5\r\nhello\r\n",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        // The server closes without the final 0-size chunk: the decoder must end
        // on EOF instead of looping or throwing.
        assertEquals("hello", String(stream.readBytes(), Charsets.US_ASCII))
      }
    }
  }

  @Test
  fun chunkedBodyWithTrailers() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
            "3\r\nabc\r\n" +
            "0\r\nX-Trailer: value\r\n\r\n",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertEquals("abc", String(stream.readBytes(), Charsets.US_ASCII))
      }
    }
  }

  @Test
  fun throwsOnBadChunkSize() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
            "zz\r\nnot-a-size\r\n",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertThrows(IOException::class.java) { stream.readBytes() }
      }
    }
  }

  @Test
  fun readAndSkipDelegateToBody() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertEquals('h'.code, stream.read())
        assertEquals(2L, stream.skip(2))
        assertEquals("lo", String(stream.readBytes(), Charsets.US_ASCII))
      }
    }
  }

  @Test
  fun throwsOnNon2xxStatus() {
    withRawResponse("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n") {
        url ->
      assertThrows(IOException::class.java) { RawSocketHttpStream.open(url) }
    }
  }

  @Test
  fun truncatedResponseHeadStillParsesAndEndsAtEof() {
    withRawResponse("HTTP/1.1 200 OK\r\nContent-Type: multipart\r\n") { url ->
      // The server closes mid-head (no blank line): the head parser hits EOF but
      // the status line is already there, so open() succeeds with an empty body.
      RawSocketHttpStream.open(url).use { stream ->
        assertTrue(stream.readBytes().isEmpty())
      }
    }
  }

  @Test
  fun toleratesHeaderLinesWithoutColon() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nNotAHeaderLine\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertEquals("hello", String(stream.readBytes(), Charsets.US_ASCII))
      }
    }
  }

  @Test
  fun emptyChunkedBodyReadsAsEmpty() {
    withRawResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n0\r\n\r\n",
    ) { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertTrue(stream.readBytes().isEmpty())
      }
    }
  }

  @Test
  fun throwsOnServerClosingWithoutResponse() {
    val serverSocket = ServerSocket(0)
    val thread = Thread {
      val client = serverSocket.accept()
      client.use { HttpRequestHead.read(it.getInputStream()) }
    }
    try {
      thread.start()
      assertThrows(IOException::class.java) {
        RawSocketHttpStream.open(URL("http://127.0.0.1:${serverSocket.localPort}/stream"))
      }
    } finally {
      serverSocket.close()
    }
  }

  @Test
  fun throwsOnNonHttpStatusLine() {
    // A status line that does not start with HTTP/1.x is rejected, even with a 2xx code.
    withRawResponse("HTTP/2.0 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n") { url ->
      assertThrows(IOException::class.java) { RawSocketHttpStream.open(url) }
    }
  }

  @Test
  fun throwsOnStatusLineWithoutStatusCode() {
    // "HTTP/1.1" has no code token: parseHead falls back to 0, out of the 2xx range.
    withRawResponse("HTTP/1.1\r\n\r\n") { url ->
      assertThrows(IOException::class.java) { RawSocketHttpStream.open(url) }
    }
  }

  @Test
  fun responseWithoutContentLengthReadsUntilClose() {
    // No Content-Length and no transfer-encoding: the body is the socket-until-close stream.
    withRawResponse("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\nhello") { url ->
      RawSocketHttpStream.open(url).use { stream ->
        assertEquals("hello", String(stream.readBytes(), Charsets.US_ASCII))
      }
    }
  }

  @Test
  fun defaultPortUsedWhenUrlHasNone() {
    // No explicit port -> url.port is -1 -> the client falls back to the default
    // HTTP port 80 (nothing listens there locally -> connection refused).
    assertThrows(IOException::class.java) {
      RawSocketHttpStream.open(URL("http://127.0.0.1/stream"))
    }
  }

  /** Serves [response] to a single GET request and runs [block] with the URL. */
  private fun withRawResponse(response: String, block: (URL) -> Unit) {
    val serverSocket = ServerSocket(0)
    val thread = Thread {
      val client = serverSocket.accept()
      client.use {
        HttpRequestHead.read(it.getInputStream())
        it.getOutputStream().apply {
          write(response.toByteArray(Charsets.US_ASCII))
          flush()
        }
      }
    }
    try {
      thread.start()
      block(URL("http://127.0.0.1:${serverSocket.localPort}/stream"))
    } finally {
      serverSocket.close()
    }
  }
}
