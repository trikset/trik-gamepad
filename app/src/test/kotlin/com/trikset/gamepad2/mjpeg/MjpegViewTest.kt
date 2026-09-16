package com.trikset.gamepad2.mjpeg

import com.trikset.gamepad2.RobolectricTestBase
import com.trikset.gamepad2.video.MjpegVideoPlayer
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class MjpegViewTest : RobolectricTestBase() {

  @Test
  fun setSourceAndStartStopPlaybackShouldNotCrash() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setSource(MjpegInputStream(ByteArrayInputStream(ByteArray(0))))
    // With an empty stream the render loop hits EOF quickly; stop it.
    view.startPlayback()
    view.stopPlayback()
  }

  @Test
  fun stopPlaybackWhenNotRunningShouldBeNoOp() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.stopPlayback()
  }

  @Test
  fun setSourceNullThenStartPlaybackShouldBeNoOp() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setSource(null)
    view.startPlayback()
  }

  @Test
  fun onSizeChangedShouldTrackDisplaySize() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(640, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(480, android.view.View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 640, 480)
    // No crash; the render thread uses the tracked size for the center crop.
  }

  @Test
  fun streamErrorListenerShouldBeSettable() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    view.setOnStreamErrorListener {}
    view.setOnStreamErrorListener(null)
  }

  @Test
  fun constructorsShouldCreateView() {
    val viaContext = MjpegView(RuntimeEnvironment.getApplication())
    val viaAttrs = MjpegView(RuntimeEnvironment.getApplication(), null)
    assertNotNull(viaContext)
    assertNotNull(viaAttrs)
    assertNull(viaContext.tag)
  }

  @Test
  fun isPlayingShouldReflectPlaybackState() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    assertFalse(view.isPlaying)
    view.setSource(MjpegInputStream(ByteArrayInputStream(ByteArray(0))))
    view.startPlayback()
    assertTrue(view.isPlaying)
    view.stopPlayback()
    assertFalse(view.isPlaying)
  }

  @Test
  fun firstFrameListenerShouldNotFireOnEmptyStream() {
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val fired = AtomicInteger(0)
    view.setOnFirstFrameListener { fired.incrementAndGet() }
    // The empty stream hits EOF immediately: an error, never a decoded frame.
    view.setSource(MjpegInputStream(ByteArrayInputStream(ByteArray(0))))
    view.startPlayback()
    Thread.sleep(200)
    view.stopPlayback()
    assertEquals(0, fired.get())
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun firstFrameListenerShouldFireOncePerPlaybackAfterFrameDecoded() {
    val server = SyntheticMjpegServer(framesPerConnection = 10, frameIntervalMs = 10)
    val port = server.start()
    val view = MjpegView(RuntimeEnvironment.getApplication())
    val fired = AtomicInteger(0)
    view.setOnFirstFrameListener { fired.incrementAndGet() }
    val url = URL("http://127.0.0.1:$port/?action=stream")
    try {
      val stream = requireNotNull(MjpegVideoPlayer(view).openStream(url.toString()))
      view.setSource(stream)
      view.startPlayback()
      val deadline = System.currentTimeMillis() + 10000
      while (fired.get() < 1 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20)
      }
      assertTrue("a decoded frame must report first-frame once", fired.get() >= 1)
      // A second playback cycle reports again (spinner re-shows on reconnect).
      view.stopPlayback()
      view.setSource(requireNotNull(MjpegVideoPlayer(view).openStream(url.toString())))
      view.startPlayback()
      assertTrue(
          "reconnect must open a fresh connection",
          server.acceptedConnections.get() >= 2,
      )
      val secondDeadline = System.currentTimeMillis() + 10000
      while (fired.get() < 2 && System.currentTimeMillis() < secondDeadline) {
        Thread.sleep(20)
      }
      assertTrue("a new playback cycle must report first-frame again", fired.get() >= 2)
    } finally {
      view.stopPlayback()
      server.stop()
    }
  }

  @Test
  fun stopPlaybackWhenRunningWithNullSourceShouldNotCrash() {
    // Render thread is blocked in a socket read (running=true), then the source
    // is cleared before stop: stopPlayback's close-guard must handle input==null.
    val server = ServerSocket(0)
    try {
      val client = Socket("127.0.0.1", server.localPort)
      try {
        val view = MjpegView(RuntimeEnvironment.getApplication())
        view.setSource(MjpegInputStream(client.getInputStream()))
        view.startPlayback()
        Thread.sleep(100)
        view.setSource(null)
        view.stopPlayback()
        assertFalse(view.isPlaying)
      } finally {
        client.close()
      }
    } finally {
      server.close()
    }
  }

  /**
   * Runs stopPlayback against a render thread blocked in a socket read and asserts the stream was
   * closed so the read unblocks.
   */
  private fun blockedReadCycle(view: MjpegView, client: Socket) {
    view.setSource(MjpegInputStream(client.getInputStream()))
    view.startPlayback()
    // Best-effort: give the render thread a moment to reach the blocking read.
    Thread.sleep(100)
    view.stopPlayback()
  }

  @Test
  fun stopPlaybackShouldCloseStreamToUnblockBlockedRead() {
    val server = ServerSocket(0)
    try {
      val client = Socket("127.0.0.1", server.localPort)
      try {
        val view = MjpegView(RuntimeEnvironment.getApplication())
        blockedReadCycle(view, client)
        assertTrue("stopPlayback must close the stream so a blocked read unblocks", client.isClosed)
      } finally {
        client.close()
      }
    } finally {
      server.close()
    }
  }

  @Test
  fun stopPlaybackShouldNotFireErrorListenerOnDeliberateStop() {
    val server = ServerSocket(0)
    try {
      val client = Socket("127.0.0.1", server.localPort)
      try {
        val view = MjpegView(RuntimeEnvironment.getApplication())
        var errors = 0
        view.setOnStreamErrorListener { errors++ }
        blockedReadCycle(view, client)
        assertEquals("deliberate stop must not trigger the reconnect listener", 0, errors)
      } finally {
        client.close()
      }
    } finally {
      server.close()
    }
  }
}
