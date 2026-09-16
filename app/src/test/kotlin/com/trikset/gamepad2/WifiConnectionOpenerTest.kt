package com.trikset.gamepad2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowNetwork

/**
 * S13 for the https path (E3): when a Wi-Fi network is present the video URLConnection must be
 * opened over it (`Network.openConnection`), fall back to the default network when none is
 * available or the network-open fails, and trust the robot's self-signed https camera endpoint. The
 * network-open and default-open calls are injectable seams (Robolectric's `ShadowNetwork` shadows
 * only `bindSocket`, not `openConnection`), so the routing decision is testable directly.
 */
@RunWith(RobolectricTestRunner::class)
class WifiConnectionOpenerTest : RobolectricTestBase() {

  private fun fakeConnection(): URLConnection =
      object : URLConnection(URL("https://127.0.0.1:1/fake")) {
        override fun connect() {}
      }

  @Test
  fun noWifiUsesTheDefaultConnection() {
    val fake = fakeConnection()
    var defaultOpened = 0
    val opener =
        WifiConnectionOpener(
            wifiNetworkProvider = { null },
            defaultOpen = { url ->
              assertEquals("https://127.0.0.1:1/x", url.toString())
              defaultOpened++
              fake
            },
        )
    assertSame(fake, opener.open(URL("https://127.0.0.1:1/x")))
    assertEquals(1, defaultOpened)
  }

  @Test
  fun wifiRoutesThroughNetworkOpenConnection() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val fake = fakeConnection()
    var networkOpened = 0
    val opener =
        WifiConnectionOpener(
            wifiNetworkProvider = { wifi },
            networkOpen = { network, url ->
              assertSame(wifi, network)
              assertEquals("https://127.0.0.1:1/x", url.toString())
              networkOpened++
              fake
            },
        )
    assertSame(fake, opener.open(URL("https://127.0.0.1:1/x")))
    assertEquals(1, networkOpened)
  }

  @Test
  fun networkOpenFailureFallsBackToTheDefaultConnection() {
    val wifi = ShadowNetwork.newInstance(nextNetworkId())
    val fake = fakeConnection()
    val opener =
        WifiConnectionOpener(
            wifiNetworkProvider = { wifi },
            networkOpen = { _, _ -> throw IOException("network vanished") },
            defaultOpen = { fake },
        )
    assertSame(fake, opener.open(URL("https://127.0.0.1:1/x")))
  }

  @Test
  fun httpsConnectionGetsTheTrustAllTlsConfig() {
    val opener = WifiConnectionOpener(wifiNetworkProvider = { null })
    val connection = opener.open(URL("https://127.0.0.1:1/x"))
    assertTrue(connection is HttpsURLConnection)
    assertSame(
        WifiConnectionOpener.trustAllSslSocketFactory,
        (connection as HttpsURLConnection).sslSocketFactory,
    )
    assertSame(WifiConnectionOpener.allowAllHostnameVerifier, connection.hostnameVerifier)
  }

  @Test
  fun httpConnectionIsLeftUntouched() {
    val opener = WifiConnectionOpener(wifiNetworkProvider = { null })
    val connection = opener.open(URL("http://127.0.0.1:1/x"))
    assertTrue(connection is HttpURLConnection)
    assertFalse(connection is HttpsURLConnection)
  }

  private companion object {
    var nextId = 1

    fun nextNetworkId(): Int = nextId++
  }
}
