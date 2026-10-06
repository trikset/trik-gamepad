package com.trikset.gamepad2

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TcpTransportTest : RobolectricTestBase() {

  @Test
  fun sendBeforeOpenReturnsFalse() {
    assertFalse(TcpTransport().send("pad 1 0 0"))
  }

  @Test
  fun sendAfterCloseReturnsFalse() {
    val transport = TcpTransport()
    setField(transport, "writer", java.io.PrintWriter(java.io.StringWriter()))
    transport.close()
    assertFalse(transport.send("pad 1 0 0"))
  }
}
