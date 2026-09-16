package com.trikset.gamepad2

import android.os.Handler
import android.os.Looper
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.mjpeg.SyntheticMjpegServer
import com.trikset.gamepad2.video.MjpegVideoPlayer
import java.net.ServerSocket
import java.net.URL
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

/**
 * End-to-end: a real [SyntheticMjpegServer] + [MjpegVideoPlayer] + [VideoRetryController]
 * reproduces the user-visible regression (video stays black after the robot leaves wifi range and
 * comes back) and proves the bounded retry recovers it. Real sockets on the PAUSED main looper; the
 * retry tick and the onPlayResult posts are driven deterministically via `idleFor`/`idle`.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(PAUSED)
class VideoStreamSelfHealingTest : RobolectricTestBase() {

  @Test
  fun videoShouldRecoverWhenRobotComesBackIntoRange() {
    val port = freePort()
    val server = SyntheticMjpegServer(port = port, framesPerConnection = 10, frameIntervalMs = 10)
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val executor = PausedExecutorService()
    val player = MjpegVideoPlayer(view, executor, Handler(Looper.getMainLooper()))
    val (controller, _) =
        retryController(view, player, URL("http://127.0.0.1:$port/?action=stream"))
    controller.onResume()

    // Robot offline: the first tick's reload fails (connection refused), the view never plays.
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    executor.runAll()
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse("an offline robot must not start playback", view.isPlaying)

    // Robot comes back: the retry tick reloads and the video starts within a bounded time.
    server.start()
    try {
      val deadline = System.currentTimeMillis() + 5000
      while (!view.isPlaying && System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        executor.runAll()
        shadowOf(Looper.getMainLooper()).idle()
      }
      assertTrue("video must recover after the robot returns", view.isPlaying)
      assertTrue(
          "the recovery must have opened a fresh connection",
          server.acceptedConnections.get() >= 1,
      )
    } finally {
      view.stopPlayback()
      server.stop()
    }
  }

  @Test
  fun midStreamDropShouldReconnectThroughController() {
    val server = SyntheticMjpegServer(framesPerConnection = 4, frameIntervalMs = 5)
    val port = server.start()
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val executor = PausedExecutorService()
    val player = MjpegVideoPlayer(view, executor, Handler(Looper.getMainLooper()))
    val (controller, reload) =
        retryController(view, player, URL("http://127.0.0.1:$port/?action=stream"))
    controller.onResume()
    try {
      reload()
      executor.runAll()
      shadowOf(Looper.getMainLooper()).idle()
      assertTrue("initial load must start playback", view.isPlaying)

      // Server drops after 4 frames -> render-thread error -> controller reloads -> new connection.
      val deadline = System.currentTimeMillis() + 15000
      while (server.acceptedConnections.get() < 2 && System.currentTimeMillis() < deadline) {
        executor.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
      }
      assertTrue(
          "controller must reconnect after a mid-stream drop",
          server.acceptedConnections.get() >= 2,
      )
    } finally {
      view.stopPlayback()
      server.stop()
    }
  }

  /**
   * Wires a short-interval retry controller to [player] for [url] and returns it with its reload
   * action (so the initial load in a test reuses the exact same play path as the retries). The
   * result listener is set once and routes every play result to the controller.
   */
  private fun retryController(
      view: MjpegView,
      player: MjpegVideoPlayer,
      url: URL,
  ): Pair<VideoRetryController, () -> Unit> {
    var controller: VideoRetryController? = null
    player.onPlayResult = { ok ->
      if (ok) controller?.onLoadSuccess() else controller?.onLoadFailed()
    }
    val reloadAction = { player.play(url.toString()) }
    controller =
        VideoRetryController(
            mainHandler = Handler(Looper.getMainLooper()),
            retryIntervalMs = 100,
            shouldReload = { !view.isPlaying },
            reload = reloadAction,
        )
    view.setOnStreamErrorListener { controller?.onStreamError() }
    return controller to reloadAction
  }

  /** Reserves a free port (bind + close) for the "offline, then back on the same address" story. */
  private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
