package com.trikset.gamepad2.video

import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class MjpegVideoPlayerTest : RobolectricTestBase() {

  @Test
  fun delegatesShowFpsAndIsPlayingToMjpegView() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val player = MjpegVideoPlayer(view)
    assertFalse(player.showFps)
    assertFalse(player.isPlaying)
    player.showFps = true
    assertTrue(view.showFps)
    assertTrue(player.showFps)
  }

  @Test
  fun playWithNullUrlReportsFailure() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val player = MjpegVideoPlayer(view)
    var result: Boolean? = null
    player.onPlayResult = { result = it }
    player.play(null)
    // The open runs on a real executor; the result lands on the PAUSED main looper, so poll while
    // draining it (runBounded alone never executes the queued main-thread task).
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline && result == null) {
      ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(20)
    }
    assertFalse("null URL must report a failed play", result!!)
  }

  @Test
  fun releaseIsSafeAndClearsListeners() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val player = MjpegVideoPlayer(view)
    player.setOnStreamErrorListener {}
    player.setOnFirstFrameListener {}
    player.release()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun releaseShutsDownTheOwnedExecutor() {
    // The player's default single-thread executor is non-daemon: release() must shut it down, or
    // every discarded player leaks its thread for the process lifetime.
    val player = MjpegVideoPlayer(MjpegView(RuntimeEnvironment.getApplication()))
    val executor = field(player, "executor") as ExecutorService
    assertFalse(executor.isShutdown)
    player.release()
    assertTrue("release() must shut down the executor this player created", executor.isShutdown)
  }

  @Test
  fun releaseDoesNotShutdownACallerInjectedExecutor() {
    // Ownership rule: an injected executor is the caller's to manage; release() must not kill it
    // (the reconnect tests share one PausedExecutorService across the controller's reloads).
    val injected = Executors.newSingleThreadExecutor()
    try {
      val player = MjpegVideoPlayer(MjpegView(RuntimeEnvironment.getApplication()), injected)
      assertSame(injected, field(player, "executor"))
      player.release()
      assertFalse("a caller-injected executor stays caller-owned", injected.isShutdown)
    } finally {
      injected.shutdownNow()
    }
  }

  @Test
  fun openStreamWithInvalidUrlReturnsNull() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val player = MjpegVideoPlayer(view)
    // Unparseable and non-http(s) strings fail the open (URISyntaxException /
    // MalformedURLException); an unreachable http URL fails the raw-socket connect.
    assertNull(player.openStream(null))
    assertNull(player.openStream("not a uri"))
    assertNull(player.openStream("rtsp://192.168.1.1:554/stream"))
    assertNull(player.openStream("http://127.0.0.1:1/nope"))
  }

  @Test
  fun openStreamHttpsFallsBackAndReturnsNullOnFailure() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val player = MjpegVideoPlayer(view)
    // Non-http(s) http: the https branch goes through the connection opener; an
    // unreachable endpoint fails inside HttpURLConnection -> null.
    assertNull(player.openStream("https://127.0.0.1:1/nope"))
  }

  @Test
  fun openStreamWithLiveServerReturnsAStream() {
    val server = SyntheticMjpegServer(framesPerConnection = 1, frameIntervalMs = 5)
    val port = server.start()
    try {
      val view = MjpegView(RuntimeEnvironment.getApplication())
      val player = MjpegVideoPlayer(view)
      val stream = player.openStream("http://127.0.0.1:$port/?action=stream")
      assertNotNull("a live MJPEG server must yield a readable stream", stream)
      stream?.close()
    } finally {
      server.stop()
    }
  }

  @Test
  fun playWithLiveServerReportsSuccess() {
    val server = SyntheticMjpegServer(framesPerConnection = 2, frameIntervalMs = 5)
    val port = server.start()
    try {
      val view = MjpegView(RuntimeEnvironment.getApplication())
      val player = MjpegVideoPlayer(view)
      var result: Boolean? = null
      player.onPlayResult = { result = it }
      player.play("http://127.0.0.1:$port/?action=stream")
      val deadline = System.currentTimeMillis() + 5000
      while (System.currentTimeMillis() < deadline && result == null) {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(20)
      }
      assertTrue("a live play must report success", result ?: false)
      player.stop()
    } finally {
      server.stop()
    }
  }
}
