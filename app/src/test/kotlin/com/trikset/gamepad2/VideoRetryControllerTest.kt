package com.trikset.gamepad2

import android.os.Handler
import android.os.Looper
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

/**
 * Deterministic retry-loop tests under the PAUSED main looper. The tick interval is shortened to
 * 1000 ms and the retry-gate predicate is injected, so the gate (a configured URL + not playing) is
 * simulated without sockets.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class VideoRetryControllerTest : RobolectricTestBase() {

  private val reloads = AtomicInteger(0)
  private var shouldReload = true
  private val timeouts = AtomicInteger(0)

  @After
  fun tearDown() {
    // Drain any leftover posted ticks so they cannot bleed into the next test.
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun controller(timeoutMs: Long = 5000): VideoRetryController {
    timeouts.set(0)
    return VideoRetryController(
        mainHandler = Handler(Looper.getMainLooper()),
        retryIntervalMs = 1000,
        loadTimeoutMs = timeoutMs,
        shouldReload = { shouldReload },
        reload = { reloads.incrementAndGet() },
        onTimeout = { timeouts.incrementAndGet() },
    )
  }

  @Test
  fun loadFailureShouldRetryOnNextTickWhileShouldReload() {
    val c = controller()
    c.onResume()
    c.onLoadFailed()
    val before = reloads.get()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
    assertTrue("a tick must reload after a failed load", reloads.get() > before)
  }

  @Test
  fun shouldNotRetryWhenGatePredicateVetoes() {
    // The injected gate vetoes every reload: no ticks fire (the app wires this to "no configured
    // URL or stream playing", not to the control state — see DECISIONS.md Option B).
    shouldReload = false
    val c = controller()
    c.onResume()
    c.onLoadFailed()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(0, reloads.get())
  }

  @Test
  fun controlConnectedEdgeShouldReloadImmediately() {
    val c = controller()
    c.onResume()
    c.onControlConnected()
    assertEquals(1, reloads.get())
  }

  @Test
  fun pauseShouldCancelRetryTicks() {
    val c = controller()
    c.onResume()
    c.onLoadFailed()
    c.onPause()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(0, reloads.get())
  }

  @Test
  fun loadSuccessShouldStopRetrying() {
    val c = controller()
    c.onResume()
    c.onLoadFailed()
    c.onLoadSuccess()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(0, reloads.get())
  }

  @Test
  fun streamErrorWhilePausedShouldReloadOnceWithoutScheduling() {
    val c = controller()
    c.onResume()
    c.onPause()
    c.onStreamError()
    // The immediate reconnect-on-error reload still happens; no tick is armed while paused.
    assertEquals(1, reloads.get())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(1, reloads.get())
  }

  @Test
  fun streamErrorWhenShouldReloadFalseShouldReloadOnce() {
    shouldReload = false
    val c = controller()
    c.onResume()
    c.onStreamError()
    assertEquals(1, reloads.get())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(1, reloads.get())
  }

  @Test
  fun controlConnectedWhilePausedShouldNotReload() {
    val c = controller()
    c.onResume()
    c.onPause()
    c.onControlConnected()
    assertEquals(0, reloads.get())
  }

  @Test
  fun controlConnectedWhenShouldReloadFalseShouldNotReload() {
    shouldReload = false
    val c = controller()
    c.onResume()
    c.onControlConnected()
    assertEquals(0, reloads.get())
  }

  @Test
  fun tickWithShouldReloadFalseShouldNotReload() {
    val c = controller()
    c.onResume()
    // A tick is scheduled; the robot drops out of range before it fires.
    shouldReload = false
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(0, reloads.get())
  }

  @Test
  fun loadFailureWhilePausedShouldNotSchedule() {
    val c = controller()
    c.onResume()
    c.onPause()
    c.onLoadFailed()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals(0, reloads.get())
  }

  @Test
  fun loadFailureShouldFireTimeoutWhenNoSuccessWithinWindow() {
    val c = controller(timeoutMs = 500)
    c.onResume()
    c.onLoadFailed()
    assertEquals("no timeout yet before the window", 0, timeouts.get())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    assertEquals("timeout must fire after the window expires", 1, timeouts.get())
    c.onLoadFailed()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    assertEquals("reloads stop after timeout", 0, reloads.get())
  }

  @Test
  fun loadSuccessBeforeTimeoutCancelsTimeout() {
    val c = controller(timeoutMs = 500)
    c.onResume()
    c.onLoadFailed()
    c.onLoadSuccess()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    assertEquals("timeout must not fire when load succeeded", 0, timeouts.get())
  }

  @Test
  fun timeoutResetByControlConnected() {
    val c = controller(timeoutMs = 500)
    c.onResume()
    c.onLoadFailed()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    assertEquals("timeout must have fired", 1, timeouts.get())
    assertEquals("no reloads after timeout", 0, reloads.get())
    shouldReload = true
    c.onControlConnected()
    assertEquals("reload must fire after timeout on reconnect", 1, reloads.get())
  }
}
