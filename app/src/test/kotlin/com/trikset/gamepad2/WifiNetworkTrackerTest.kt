package com.trikset.gamepad2

import android.content.Context
import android.net.ConnectivityManager
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.video.MjpegVideoPlayer
import java.net.DatagramSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * Regression lock for the ConnectivityManager$TooManyRequestsException crash (2026-09-05, seen in
 * the field): Wi-Fi network callbacks are a per-app budgeted resource, and the old code registered
 * one per binder/opener/player construction (a new tracker per connect attempt + two per video
 * player) with no unregister, until Android refused another registration and the resulting
 * RuntimeException crashed the connect thread. The tracker is now a single process-wide instance
 * registered once by [App]; these tests prove construction never registers again.
 */
@RunWith(RobolectricTestRunner::class)
class WifiNetworkTrackerTest : RobolectricTestBase() {

  private val context: Context = RuntimeEnvironment.getApplication()

  private fun networkCallbackCount(): Int =
      (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).let { cm ->
        shadowOf(cm).getNetworkCallbacks().count { it is ConnectivityManager.NetworkCallback }
      }

  @Test
  fun appRegistersExactlyOneSharedTrackerAndInitializationIsIdempotent() {
    // App.onCreate (booted by Robolectric for every test) initializes the process-wide tracker, so
    // exactly ONE network callback exists before the test body does anything.
    assertEquals(1, networkCallbackCount())
    val shared = WifiNetworkTracker.instance()
    assertNotNull(shared)
    assertSame(shared, WifiNetworkTracker.instance())
    assertSame(
        "re-initialization must reuse the single instance, never register again",
        shared,
        WifiNetworkTracker.initialize(context),
    )
    assertEquals(1, networkCallbackCount())
  }

  @Test
  fun constructingBindersOpenersAndPlayersRegistersNoCallbacks() {
    assertEquals(1, networkCallbackCount())
    repeat(4) {
      WifiSocketBinder()
      WifiDatagramBinder()
      WifiConnectionOpener()
      MjpegVideoPlayer(MjpegView(context))
    }
    assertEquals(
        "constructing binders/openers/players must never add a network callback",
        1,
        networkCallbackCount(),
    )
  }

  @Test
  fun defaultProvidersFallBackBeforeTheAppInitializesTheTracker() {
    // Before App.onCreate ran (simulated by the test-only reset), the default Wi-Fi provider has no
    // tracker to read and every binder falls back to the default network instead of throwing.
    WifiNetworkTracker.resetForTest()
    assertEquals(null, WifiNetworkTracker.instance())
    val wifi = ShadowNetwork.newInstance(1)
    WifiSocketBinder().bind(Socket()).let { s ->
      assertFalse(shadowOf(wifi).isSocketBound(s))
      s.close()
    }
    WifiDatagramBinder().bind(DatagramSocket()).let { s ->
      assertFalse(shadowOf(wifi).isSocketBound(s))
      s.close()
    }
    // The opener's default provider is the same shared tracker; assert it routes to defaultOpen.
    var defaultOpened = 0
    val opener =
        WifiConnectionOpener(
            defaultOpen = { url ->
              defaultOpened++
              object : java.net.URLConnection(url) {
                override fun connect() {}
              }
            }
        )
    opener.open(java.net.URL("http://127.0.0.1:1/x"))
    assertEquals(1, defaultOpened)
  }
}
