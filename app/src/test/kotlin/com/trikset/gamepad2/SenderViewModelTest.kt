package com.trikset.gamepad2

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SenderViewModelTest : RobolectricTestBase() {

  @Test
  fun onClearedDisconnectsTheSenderWithoutThrowing() {
    val viewModel = SenderViewModel(RuntimeEnvironment.getApplication())
    val sender = SenderService()
    viewModel.sender = sender
    assertTrue(viewModel.connectionState.value is ConnectionState.Disconnected)
    // onCleared is the protected socket-close path (ViewModel contract); it must
    // run the disconnect without throwing even when nothing is connected.
    SenderViewModel::class
        .java
        .getDeclaredMethod("onCleared")
        .apply { isAccessible = true }
        .invoke(viewModel)
    assertTrue(viewModel.connectionState.value is ConnectionState.Disconnected)
  }

  @Test
  fun udpModeFactoryBuildsAWifiBoundDatagramTransport() {
    // The ViewModel's default sender carries the Wi-Fi-bound UDP factory (line 23); exercising a
    // real UDP connect through it covers that branch. The bind falls back to the default network
    // when no Wi-Fi transport exists (Robolectric), so a localhost server still receives.
    TestUdpServer().use { server ->
      val viewModel = SenderViewModel(RuntimeEnvironment.getApplication())
      val sender = viewModel.sender
      sender.transportMode = TransportMode.UDP
      sender.setTarget(TestUdpServer.HOST, server.port)
      sender.keepaliveTimeout = 10000000 // disable keepalive noise
      sender.send("pad 1 0 0")
      // The sender uses a real executor here (the ViewModel's default), so await on the server.
      assertTrue("UDP command must reach the server", server.awaitReceived("pad 1 0 0"))
      sender.disconnect("test done")
    }
  }

  @Test
  fun tcpModeFactoryBuildsAWifiBoundTcpTransport() {
    // The TCP twin: the ViewModel's default sender also carries the lazily-built Wi-Fi-bound TCP
    // binder, exercised over a real TCP connect to a local server (the TCP branch of the factory).
    TestTcpServer().use { server ->
      val viewModel = SenderViewModel(RuntimeEnvironment.getApplication())
      val sender = viewModel.sender
      sender.setTarget("127.0.0.1", server.port)
      sender.keepaliveTimeout = 10000000 // disable keepalive noise
      sender.send("pad 1 0 0")
      val drain = { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
      assertTrue("TCP command must reach the server", server.awaitReceived("pad 1 0 0", drain))
      sender.disconnect("test done")
    }
  }
}
