package com.trikset.gamepad2

import android.net.Network
import java.net.Socket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * A2: sockets must prefer the robot's Wi-Fi network when one is present (the default network can be
 * cellular), and fall back to the default network otherwise. The network lookup is the injectable
 * [WifiSocketBinder] seam, so these tests supply fixed [Network]s via [ShadowNetwork] instead of
 * faking `ConnectivityManager` capabilities (the `NetworkCapabilities.Builder` class is absent from
 * the SDK stub jars).
 */
@RunWith(RobolectricTestRunner::class)
class SocketBinderTest : RobolectricTestBase() {

  @Test
  fun noWifiProviderFallsBackToTheGivenSocket() {
    val binder = WifiSocketBinder { null }
    val socket = Socket()
    // No Wi-Fi network -> the socket is returned untouched (default-network fallback).
    assertSame(socket, binder.bind(socket))
    socket.close()
  }

  @Test
  fun wifiProviderBindsTheSocketToTheWifiNetwork() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiSocketBinder { wifi }
    val socket = binder.bind(Socket())
    assertTrue(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  fun nullProviderAfterWifiLostLeavesTheSocketUnbound() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val binder = WifiSocketBinder { null }
    val socket = binder.bind(Socket())
    assertFalse(shadowOf(wifi).isSocketBound(socket))
    socket.close()
  }

  @Test
  fun defaultBinderRoutesThroughTheAppSharedTracker() {
    // The default Wi-Fi provider is the process-wide WifiNetworkTracker registered once by
    // App.onCreate — never a per-binder tracker (the old per-construction trackers accumulated
    // network callbacks until ConnectivityManager$TooManyRequestsException crashed the connect
    // path). The callback-driving body is shared with the datagram twin in
    // assertDefaultBinderFollowsAppTracker.
    val binder = WifiSocketBinder()
    assertDefaultBinderFollowsAppTracker(
        bind = { binder.bind(Socket()) },
        isBound = { socket, wifi -> shadowOf(wifi).isSocketBound(socket) },
    )
  }

  private companion object {
    var nextId = 1

    fun nextNetworkId(): Int = nextId++
  }
}
