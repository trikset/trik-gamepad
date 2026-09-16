package com.trikset.gamepad2.mjpeg

import java.io.InputStream

/**
 * Reads a raw HTTP request/response head from [input] until the terminating CRLF CRLF, discarding
 * the bytes (the client's write must not block the server). Shared by [SyntheticMjpegServer] and
 * the RawSocketHttpStreamTest fixture.
 */
object HttpRequestHead {
  private val crlfCrlf =
      byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())

  fun read(input: InputStream) {
    var matched = 0
    while (matched < crlfCrlf.size) {
      val b = input.read()
      if (b < 0) return
      if (b.toByte() == crlfCrlf[matched]) {
        matched++
      } else {
        matched = if (b == '\r'.code) 1 else 0
      }
    }
  }
}
