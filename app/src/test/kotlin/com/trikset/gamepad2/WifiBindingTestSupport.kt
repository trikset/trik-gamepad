package com.trikset.gamepad2

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork

/**
 * Shared helper for the Socket/Datagram binder default-provider tests (SocketBinderTest /
 * WifiDatagramBinderTest): drives the ONE network callback the app-shared [WifiNetworkTracker]
 * registered at App.onCreate and asserts [bind] follows it — when the Wi-Fi network appears the
 * bind must use it, an unrelated network disappearing must not clear it, and the tracked network's
 * onLost must fall back to the default network. [bind] performs one bind (fresh resource returned,
 * closed by the helper); [isBound] reports whether that resource was bound to the given [Network].
 * The count guard (exactly one registration) lives in WifiNetworkTrackerTest.
 */
fun <R : AutoCloseable> assertDefaultBinderFollowsAppTracker(
    bind: () -> R,
    isBound: (R, Network) -> Boolean,
) {
  val cm =
      (RuntimeEnvironment.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE))
          as ConnectivityManager
  val appCallbacks =
      shadowOf(cm).getNetworkCallbacks().filter { it is ConnectivityManager.NetworkCallback }
  check(appCallbacks.size == 1) {
    "App.onCreate must register exactly one Wi-Fi network callback, found ${appCallbacks.size}"
  }
  val callback = appCallbacks.single() as ConnectivityManager.NetworkCallback
  val wifi = ShadowNetwork.newInstance(NETWORK_ID.incrementAndGet())

  callback.onAvailable(wifi)
  bind().use { bound ->
    check(isBound(bound, wifi)) { "an available Wi-Fi network must be used by the default binder" }
  }

  // An unrelated network disappearing must not clear the tracked one.
  callback.onLost(ShadowNetwork.newInstance(NETWORK_ID.incrementAndGet()))
  bind().use { stillBound ->
    check(isBound(stillBound, wifi)) {
      "an unrelated onLost must not clear the tracked network"
    }
  }

  callback.onLost(wifi)
  bind().use { fallback ->
    check(!isBound(fallback, wifi)) {
      "the tracked network's onLost must fall back to the default network"
    }
  }
}

private val NETWORK_ID = AtomicInteger(0)
