package com.trikset.gamepad2

import java.net.DatagramSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

/**
 * A2/S13: UDP datagram sockets must prefer the robot's Wi-Fi network when one is present (the
 * default network can be cellular), and fall back to the default network otherwise. The UDP twin of
 * [SocketBinderTest]; [WifiDatagramBinder] binds via `Network.bindSocket(DatagramSocket)`.
 */
@RunWith(RobolectricTestRunner::class)
class WifiDatagramBinderTest : RobolectricTestBase() {

  @Test
  fun noWifiProviderFallsBackToTheGivenSocket() {
    val binder = WifiDatagramBinder(wifiNetworkProvider = { null })
    val socket = DatagramSocket()
    // No Wi-Fi network -> the socket is returned untouched (default-network fallback).
    assertSame(socket, binder.bind(socket))
    socket.close()
  }

  @Test
  @Config(sdk = [Config.TARGET_SDK])
  fun wifiProviderBindsTheDatagramSocketToTheWifiNetwork() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiDatagramBinder(wifiNetworkProvider = { wifi })
    val socket = binder.bind(DatagramSocket())
    assertTrue(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  @Config(sdk = [Config.TARGET_SDK])
  fun defaultBinderRoutesThroughTheAppSharedTracker() {
    // The default Wi-Fi provider is the process-wide WifiNetworkTracker registered once by
    // App.onCreate — never a per-binder tracker (the old per-construction trackers accumulated
    // network callbacks until ConnectivityManager$TooManyRequestsException crashed the connect
    // path). The callback-driving body is shared with the TCP twin in
    // assertDefaultBinderFollowsAppTracker.
    val binder = WifiDatagramBinder()
    assertDefaultBinderFollowsAppTracker(
        bind = { binder.bind(DatagramSocket()) },
        isBound = { socket, wifi -> shadowOf(wifi).isSocketBound(socket) },
    )
  }

  @Test
  fun sdkBelow22SkipsBindSocket() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiDatagramBinder({ wifi }, { false })
    val socket = binder.bind(DatagramSocket())
    assertFalse(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  private companion object {
    var nextId = 1

    fun nextNetworkId(): Int = nextId++
  }
}
