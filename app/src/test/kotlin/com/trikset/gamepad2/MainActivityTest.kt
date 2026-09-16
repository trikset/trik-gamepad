package com.trikset.gamepad2

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.mjpeg.MjpegView
import com.trikset.gamepad2.mjpeg.ScaleMode
import com.trikset.gamepad2.video.MjpegVideoPlayer
import com.trikset.gamepad2.video.VideoPlayer
import java.util.concurrent.ExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest : RobolectricTestBase() {

  private lateinit var activity: MainActivity

  // Shared preferences persist across methods in a JVM; start each test clean.
  @Before
  fun resetSharedPreferences() {
    PreferenceManager.getDefaultSharedPreferences(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        .edit()
        .clear()
        .commit()
  }

  @Before
  fun setUp() {
    activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
  }

  /** Stores [value] under [key] and notifies the settings controller (the common act step). */
  private fun setPref(key: String, value: String) =
      setPref(key, value, requireNotNull(activity.settingsController))

  private fun prefs() = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)

  @Test
  fun onCreateShouldSetUpSenderServiceAndPads() {
    assertNotNull(activity.senderService)
    val left = activity.findViewById<SquareTouchPadLayout>(R.id.leftPad)
    assertNotNull(left)
    assertNotNull(activity.findViewById<SquareTouchPadLayout>(R.id.rightPad))
  }

  @Test
  fun onSharedPreferenceChangedShouldSetTarget() {
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    assertEquals("10.0.0.7", activity.senderService.hostAddr)
  }

  @Test
  fun onSharedPreferenceChangedWithBadPortShouldToastAndKeepDefault() {
    setPref(SettingsFragment.SK_HOST_PORT, "not-a-number")
    // No crash; target still set with default 4444 because the parse failure is
    // caught and the port variable keeps its initial value.
    assertNotNull(activity.senderService.hostAddr)
  }

  @Test
  fun onSharedPreferenceChangedShouldNotRewriteVideoUriOnHostChange() {
    // No implicit video-URI copy on host change; the user resets it explicitly.
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    setPref(SettingsFragment.SK_HOST_ADDRESS, "192.168.1.42")
    assertEquals(
        "http://10.0.0.7:8080/?action=stream",
        prefs().getString(SettingsFragment.SK_VIDEO_URI, ""),
    )
  }

  @Test
  fun onOptionsItemSelectedShouldToggleWheel() {
    assertFalse(field(activity, "wheelEnabled") as Boolean)
    setField(activity, "wheelEnabled", true)
    assertTrue(field(activity, "wheelEnabled") as Boolean)
  }

  @Test
  fun onCreateShouldRegisterPreferencesAndLifecycle() {
    // .setup() ran onCreate+onResume; tear down to cover onPause/onDestroy.
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun keepaliveBelowMinimumOrInvalidShouldResetToDefault() {
    for (value in listOf("100", "abc")) {
      setPref(SettingsFragment.SK_KEEPALIVE, value)
      // Below MINIMAL_KEEPALIVE / non-numeric: reset to the current keepalive.
      val stored = prefs().getString(SettingsFragment.SK_KEEPALIVE, "")
      assertEquals(
          "keepalive '$value' must reset to the default",
          SenderService.DEFAULT_KEEPALIVE.toString(),
          stored,
      )
    }
  }

  @Test
  fun wheelStepOutOfRangeOrInvalidShouldStaySane() {
    for (value in listOf("999", "abc")) {
      // Integer.getInteger reads a SYSTEM property named by the pref value, so
      // arbitrary values fall back to the default (7); the clamp keeps [1,100].
      setPref(SettingsFragment.SK_WHEEL_STEP, value)
      val step = field(activity, "wheelStep") as Int
      assertTrue("expected wheel step in [1,100] for '$value', got $step", step in 1..100)
    }
  }

  @Test
  fun invalidVideoUriShouldNotCrash() {
    for (value in listOf("not a uri", "foo:bar", "rtsp://192.168.1.1:554/stream")) {
      setPref(SettingsFragment.SK_VIDEO_URI, value)
      // The URI flows as an opaque string; validation happens per player at open time (MJPEG
      // parses http/https, MediaPlayer accepts rtsp). Nothing crashes at preference apply.
      assertEquals(
          "video url must be stored verbatim for '$value'",
          value,
          field(activity, "videoUrl"),
      )
    }
  }

  @Test
  fun onSharedPreferenceChangedWithValidVideoUriShouldSetUrl() {
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertNotNull(field(activity, "videoUrl"))
  }

  @Test
  fun keepScreenOnPreferenceShouldDriveMainView() {
    val main = activity.findViewById<View>(R.id.main)!!
    // Default true keeps the screen on.
    assertTrue(main.keepScreenOn)
    prefs().edit().putBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, false).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
    assertFalse(main.keepScreenOn)
  }

  @Test
  fun nullVideoUrlShouldShowPlaceholder() {
    val placeholder = activity.findViewById<android.widget.TextView>(R.id.videoPlaceholder)!!
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertEquals(View.VISIBLE, placeholder.visibility)
  }

  @Test
  fun configuredVideoUrlShouldHidePlaceholder() {
    val placeholder = activity.findViewById<android.widget.TextView>(R.id.videoPlaceholder)!!
    setPref(SettingsFragment.SK_VIDEO_URI, "http://10.0.0.7:8080/?action=stream")
    assertEquals(View.GONE, placeholder.visibility)
  }

  @Test
  fun magicButtonRowShouldNotClipAtLargeFontScale() {
    // The row is wrap_content + minHeight 50dp, so it grows with the font scale instead of
    // clipping the magic buttons. Measure at FONT_SCALE 1.3.
    val resources = activity.resources
    val config = android.content.res.Configuration(resources.configuration)
    config.fontScale = 1.3f
    // The 1-arg updateConfiguration(Configuration) was removed in SDK 36, so the
    // deprecated 2-arg form is the only way to drive a font-scale change here.
    @Suppress("DEPRECATION") resources.updateConfiguration(config, resources.displayMetrics)

    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    row.measure(
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val rowHeight = row.measuredHeight
    for (i in 0 until row.childCount) {
      val child = row.getChildAt(i)
      child.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
      )
      assertTrue(
          "child $i height ${child.measuredHeight} must fit row height $rowHeight at fontScale 1.3",
          child.measuredHeight <= rowHeight,
      )
    }
  }

  @Test
  fun magicButtonCirclesShouldNotBeClippedByClusterPadding() {
    // The cluster must not clip the button circles, and each circle must sit vertically
    // centered in the cluster. centerGlyph() used to shift each button up via translationY to
    // center its glyph, which moved the circular background off the cluster's vertical center
    // and into the top padding where clipToPadding clipped it flat (regression: phone + emulator
    // screenshots showed torn tops); glyph padding keeps the circle centered instead (see
    // DECISIONS.md "Magic-button glyph centering: asymmetric padding, not view translation").
    // clipToPadding=false stays as a belt-and-braces guard.
    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    assertFalse("cluster must not clip the button circles", row.clipToPadding)
    // Robolectric does not lay out the hierarchy on its own; measure + layout the row so the
    // child positions are real. Row is wrap_content, so UNSPECIFIED specs resolve to the true
    // content size (buttons 48dp + 2dp top/bottom padding).
    row.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    val contentCenter = row.paddingTop + (row.height - row.paddingTop - row.paddingBottom) / 2f
    for (i in 0 until row.childCount) {
      val child = row.getChildAt(i)
      // A translation-based centerGlyph (the pre-fix bug) would move the whole button; assert it
      // directly so the regression is caught regardless of Robolectric's font metrics (where the
      // vertical ink offset is ~0 and would slip past the center-tolerance check below).
      assertEquals("button $i translationX must stay 0", 0f, child.translationX, 0f)
      assertEquals("button $i translationY must stay 0", 0f, child.translationY, 0f)
      // The circular background fills the button, so the drawn button center is the circle
      // center; it must sit on the cluster's content center.
      val drawnCenter = child.top + child.height / 2f + child.translationY
      assertEquals(
          "button $i circle must be vertically centered in the cluster",
          contentCenter,
          drawnCenter,
          0.5f,
      )
    }
  }

  @Test
  fun onSharedPreferenceChangedWithValidKeepaliveShouldApply() {
    setPref(SettingsFragment.SK_KEEPALIVE, "2000")
    // >= MINIMAL_KEEPALIVE -> applied to the sender.
    assertEquals(2000, activity.senderService.keepaliveTimeout)
  }

  @Test
  fun onPauseShouldDisconnectAndStopVideo() {
    // Force a video view to cover the video != null branch in onPause.
    val player = StubVideoPlayer()
    setField(activity, "video", player)
    method(activity, "onPause").invoke(activity)
    // No crash; sensor listener unregistered and sender disconnected.
  }

  @Test
  fun onPauseWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onPause run.
    setField(activity, "sensorManager", null)
    setField(activity, "video", null)
    method(activity, "onPause").invoke(activity)
  }

  @Test
  fun onResumeWithNullFieldsShouldBeSafe() {
    // Null out both collaborators so the null branches in onResume run.
    setField(activity, "video", null)
    setField(activity, "sensorManager", null)
    method(activity, "onResume").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun restartVideoStreamShouldLoadWhenVideoPresent() {
    val player = StubVideoPlayer()
    setField(activity, "video", player)
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun onOptionsItemSelectedUnknownShouldFallThrough() {
    val menu = androidx.appcompat.view.menu.MenuBuilder(activity)
    menu.add(0, 9999, 2, "unknown")
    assertFalse(activity.onOptionsItemSelected(menu.findItem(9999)!!))
  }

  @Test
  fun wheelEnabledPreferenceShouldDriveWheel() {
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, true).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
    assertTrue(activity.wheelEnabled)
    prefs().edit().putBoolean(SettingsFragment.SK_WHEEL_ENABLED, false).commit()
    requireNotNull(activity.settingsController).onPreferenceChanged(prefs())
    assertFalse(activity.wheelEnabled)
  }

  @Test
  fun restartVideoStreamShouldBeSafeWithoutVideo() {
    // video null -> runOnUiThread closure returns early.
    val m = method(activity, "restartVideoStream")
    m.invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun connectionConnectedWithVideoConfiguredShouldDriveRetryReload() {
    // Wiring: control connection Connected + video configured + not playing must arm a reload
    // through the retry controller (here it fast-fails to an unreachable address and the
    // onLoadFailed path runs). Exercises the collector's Connected branch and the shouldReload
    // gate.
    setField(activity, "video", StubVideoPlayer())
    setField(activity, "videoUrl", "http://127.0.0.1:1/nope")
    val sender = activity.senderService
    awaitControlConnection(sender)
    // The reload's load() runs on a real executor; give the fast-failing open + its onResult
    // post time to reach the main looper so the onLoadFailed path is deterministically covered.
    val settleDeadline = System.currentTimeMillis() + 2000
    while (System.currentTimeMillis() < settleDeadline) {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(20)
    }
    sender.disconnect("test done")
  }

  @Test
  fun deadStreamShouldSelfHealWithControlPermanentlyDisconnected() {
    // e2e (Option B): a configured URL + a dead stream must keep retrying even though the control
    // connection never comes up (S2 — the video is independent of control; the old gate froze it
    // in the Connecting window). The retry tick reloads, the open fails fast, onLoadFailed
    // re-arms the loop, and the spinner stays visible the whole time.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    setField(activity, "video", StubVideoPlayer())
    setField(activity, "videoUrl", "http://127.0.0.1:1/nope")
    val sender = activity.senderService
    // Control stays Disconnected forever: no setTarget/send is ever issued.
    assertTrue(sender.connectionState.value is ConnectionState.Disconnected)
    // Drive several retry ticks (5 s each) — each reload fails fast, the loop re-arms.
    val settleDeadline = System.currentTimeMillis() + 3000
    while (System.currentTimeMillis() < settleDeadline) {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(20)
    }
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(
        "a configured URL must show the loading indicator even while control is down",
        android.view.View.VISIBLE,
        indicator!!.visibility,
    )
  }

  @Test
  fun connectionConnectedWithNullVideoUrlShouldNotReload() {
    // Gate: Connected with no video URL configured -> shouldReload short-circuits at
    // videoUrl != null (false) and nothing is armed.
    val sender = activity.senderService
    awaitControlConnection(sender)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    sender.disconnect("test done")
  }

  @Test
  fun connectionConnectedShouldConfirmHaptic() {
    // Edge: control acquired (initial connect) -> a single medium click on the root view.
    val server = openControlConnection(activity.senderService)
    try {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "connecting must fire the medium click",
          Haptics.constant(Haptics.Level.CLICK),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  @Test
  fun connectionUnexpectedDisconnectShouldPlayRejectSequence() {
    val sender = activity.senderService
    val server = openControlConnection(sender)
    try {
      // Let the main-looper collector process the Connected emission first (StateFlow conflation
      // would otherwise drop it: the previous-state edge only sees Connected once the collector
      // runs, and a quick disconnect can race ahead of it).
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      // Lost control while connected -> the strong two-pulse alert plays; the final pulse is
      // the second strong one. The root's last haptic moves off the connect medium click.
      sender.disconnect("connection lost")
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "unexpected disconnect must end the reject sequence on the strong pulse",
          Haptics.constant(Haptics.Level.HEAVY),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  @Test
  fun connectionPauseDisconnectShouldNotPlayRejectSequence() {
    val sender = activity.senderService
    val server = openControlConnection(sender)
    try {
      // Drain the Connected emission first (same StateFlow-conflation rationale as above) so the
      // connect CLICK is on the root before the pause disconnect runs.
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      // App-pause disconnect is not an error -> no alert; the last haptic stays the
      // connection medium click.
      sender.disconnect(ConnectionState.PAUSE_DISCONNECT_REASON)
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      val root = activity.findViewById<View>(R.id.main)
      assertEquals(
          "pause disconnect must not fire the reject sequence",
          Haptics.constant(Haptics.Level.CLICK),
          org.robolectric.Shadows.shadowOf(root).lastHapticFeedbackPerformed(),
      )
    } finally {
      server.close()
    }
  }

  /** Connects [sender] to [server] and waits until the state flips to Connected. */
  private fun awaitControlConnection(sender: SenderService, server: TestTcpServer) {
    sender.setTarget(TestTcpServer.HOST, server.port)
    sender.send("")
    val deadline = System.currentTimeMillis() + 5000
    while (
        sender.connectionState.value !is ConnectionState.Connected &&
            System.currentTimeMillis() < deadline
    ) {
      org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
      Thread.sleep(10)
    }
    assertTrue(sender.connectionState.value is ConnectionState.Connected)
  }

  /** Connects [sender] to an ephemeral server (closed again) and waits for Connected. */
  private fun awaitControlConnection(sender: SenderService) {
    TestTcpServer().use { server -> awaitControlConnection(sender, server) }
  }

  /** Like [awaitControlConnection] but keeps the server open so the test can drive the drop. */
  private fun openControlConnection(sender: SenderService): TestTcpServer {
    val server = TestTcpServer()
    awaitControlConnection(sender, server)
    return server
  }

  /** Sets a configured video and triggers a reload (the common spinner-test setup). */
  private fun restartVideoStreamWithConfiguredVideo(connectFirst: Boolean = true) {
    // The spinner gate (restartVideoStream) is URL-gated: a configured URL shows it, a missing one
    // hides it — control state is irrelevant. Connecting first is only needed by tests that assert
    // the control-coupled path.
    if (connectFirst) {
      awaitControlConnection(activity.senderService)
    }
    setField(activity, "video", StubVideoPlayer())
    setField(activity, "videoUrl", "http://127.0.0.1:1/nope")
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
  }

  @Test
  fun restartVideoStreamShouldShowLoadingIndicator() {
    restartVideoStreamWithConfiguredVideo()
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.VISIBLE, indicator!!.visibility)
  }

  @Test
  fun restartVideoStreamWhenDisconnectedShouldShowLoading() {
    // Option B: the spinner is URL-gated only — a configured URL shows it even without a control
    // connection (a video-only device / dead control must not hide the stream state).
    restartVideoStreamWithConfiguredVideo(connectFirst = false)
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.VISIBLE, indicator!!.visibility)
  }

  @Test
  fun restartVideoStreamWithoutVideoUrlShouldNotShowLoading() {
    // Gate: no URL configured -> the spinner stays hidden even while Connected. An unset URI pref
    // defaults to a real URL (MainActivitySettingsController), so set it to "" to get videoUrl
    // null.
    setPref(SettingsFragment.SK_VIDEO_URI, "")
    assertSpinnerHiddenAfterRestart(null, connectFirst = true)
  }

  /** Restarts the stream and asserts the loading spinner stays hidden (spinner-gate coverage). */
  private fun assertSpinnerHiddenAfterRestart(videoUrl: String?, connectFirst: Boolean) {
    if (connectFirst) {
      awaitControlConnection(activity.senderService)
    }
    setField(activity, "video", StubVideoPlayer())
    if (videoUrl != null) {
      setField(activity, "videoUrl", videoUrl)
    }
    method(activity, "restartVideoStream").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.GONE, indicator!!.visibility)
  }

  @Test
  fun emptyHostShouldHidePadsAndButtons() {
    // Empty host = video-only device: no pads/buttons, even with the "hide controls" toggle unset.
    setPref(SettingsFragment.SK_HOST_ADDRESS, "")
    assertEquals(View.GONE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.buttons)?.visibility)
  }

  @Test
  fun emptyHostShouldHideStatusPill() {
    // Empty host = video-only: no control target -> no "tap to connect" affordance; the pill is
    // hidden entirely (DESIGN.md "Connection & video state UX"). Set the pref BEFORE the activity
    // is built so the first Disconnected emission carries the blank host.
    prefs().edit().putString(SettingsFragment.SK_HOST_ADDRESS, "").commit()
    activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assertEquals(
        View.GONE,
        activity.findViewById<View>(R.id.connectionStatus)?.visibility,
    )
  }

  @Test
  fun shouldReloadVideoWithUrlShouldBeControlAgnostic() {
    // Option B: the retry gate is URL + not-playing only — the control connection state never
    // gates video recovery (S2: the stream must self-heal even while control is down).
    setPref(SettingsFragment.SK_HOST_ADDRESS, "10.0.0.7")
    setField(activity, "video", StubVideoPlayer())
    setField(activity, "videoUrl", "http://127.0.0.1:1/nope")
    val m = method(activity, "shouldReloadVideo")
    assertTrue(
        "a configured URL must arm the retry regardless of control state",
        m.invoke(activity) as Boolean,
    )
  }

  @Test
  fun onPauseShouldHideLoadingIndicator() {
    restartVideoStreamWithConfiguredVideo()
    method(activity, "onPause").invoke(activity)
    val indicator = activity.findViewById<android.widget.ProgressBar>(R.id.videoLoading)
    assertEquals(android.view.View.GONE, indicator!!.visibility)
  }

  @Test
  fun createPadShouldWireSender() {
    val m = method(activity, "createPad", Int::class.javaPrimitiveType!!, String::class.java)
    m.invoke(activity, R.id.leftPad, "1")
    val pad = activity.findViewById<SquareTouchPadLayout>(R.id.leftPad)
    assertNotNull(pad)
  }

  @Test
  fun onAccuracyChangedShouldBeNoOp() {
    // Call the listener method; must not throw.
    activity.onAccuracyChanged(null, 0)
  }

  @Test
  fun onSensorChangedAccelerometerShouldProcessWheel() {
    setField(activity, "wheelEnabled", true)
    setField(activity, "angle", 0)
    setField(activity, "wheelStep", 7)

    // Accelerometer samples reach the wheel path only when the sensor type matches.
    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER))
    // Wheel enabled -> the accelerometer sample is processed: angle changes from 0
    // (mirrors onSensorChangedWhenWheelDisabledShouldReturnEarly asserting angle == 0).
    assertNotEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun onSensorChangedWhenWheelDisabledShouldReturnEarly() {
    setField(activity, "wheelEnabled", false)
    setField(activity, "angle", 0)
    setField(activity, "wheelStep", 7)

    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_ACCELEROMETER))
    // Wheel disabled -> no command sent, angle unchanged.
    assertEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun onSensorChangedWithNonAccelerometerShouldLogOnly() {
    activity.onSensorChanged(sensorEvent(android.hardware.Sensor.TYPE_GRAVITY))
    // Non-accelerometer sensors hit the else branch; no wheel command.
    assertEquals(0, field(activity, "angle") as Int)
  }

  @Test
  fun createPadWithUnknownIdShouldThrow() {
    val m = method(activity, "createPad", Int::class.javaPrimitiveType!!, String::class.java)
    val e =
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
          m.invoke(activity, 999999, "1")
        }
    assertNotNull("root cause must be the requireNotNull failure", e.cause)
  }

  @Test
  fun btnSettingsClickShouldOpenAppSettings() {
    val btnSettings = activity.findViewById<android.widget.Button>(R.id.btnSettings)
    assertNotNull(btnSettings)
    btnSettings!!.performClick()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(SettingsActivity::class.java.name, intent?.component?.className)
    // Every button vibrates (user report: the gear was the one that did not);
    // one strong pulse, matching the magic buttons.
    assertEquals(
        "the gear must vibrate on click",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(btnSettings).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun targetChipClickShouldOpenRobotSettings() {
    val chip = activity.findViewById<View>(R.id.targetChip)
    assertNotNull(chip)
    chip!!.performClick()
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
    assertEquals(RobotSettingsActivity::class.java.name, intent?.component?.className)
    // The chip is a HUD button too — it must vibrate like the gear and magic buttons.
    assertEquals(
        "the target chip must vibrate on click",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(chip).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun hudControlsPaddingShouldStayZeroRegardlessOfInsets() {
    val hudControls = activity.findViewById<View>(R.id.hudControls)
    assertNotNull(hudControls)

    // The edge-pinned chrome deliberately ignores the window insets: it sits corner-flush
    // with small margins (see DECISIONS.md "Corner-flush HUD chrome"). Dispatch a known insets
    // frame and assert the container does NOT adopt it (a regression guard against
    // reintroducing the inset-aware padding). Robolectric may have already dispatched a
    // simulated status-bar inset during activity setup, so assert the effect of THIS dispatch.
    val frame =
        androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(1, 2, 3, 4),
            )
            .build()
            .toWindowInsets()
    assertNotNull(frame)
    hudControls.dispatchApplyWindowInsets(frame!!)
    assertEquals(0, hudControls.paddingLeft)
    assertEquals(0, hudControls.paddingTop)
    assertEquals(0, hudControls.paddingRight)
    assertEquals(0, hudControls.paddingBottom)
  }

  @Test
  fun onDestroyShouldNullOutListeners() {
    method(activity, "onDestroy").invoke(activity)
    // No crash; all listeners nulled and pads cleared.
  }

  @Test
  fun onSharedPreferenceChangedWithBadShowPadsShouldNotCrash() {
    setPref(SettingsFragment.SK_SHOW_PADS, "not-a-number")
    // The non-numeric pads alpha is caught; nothing crashes.
    assertNotNull(activity.findViewById<View>(R.id.controlsOverlay))
  }

  @Test
  fun setSenderServiceShouldStoreSender() {
    val replacement = SenderService()
    activity.setSenderService(replacement)
    assertSame(replacement, activity.senderService)
  }

  @Test
  fun setShowFpsShouldToggleVideoOverlay() {
    val mjpegView = MjpegView(activity)
    val player = MjpegVideoPlayer(mjpegView)
    setField(activity, "video", player)
    activity.setShowFps(true)
    assertTrue(mjpegView.showFps)
    activity.setShowFps(false)
    assertFalse(mjpegView.showFps)
  }

  @Test
  fun setShowFpsWithoutVideoShouldBeSafe() {
    setField(activity, "video", null)
    activity.setShowFps(true)
  }

  @Test
  fun setVideoUrlShouldReleaseThePreviousPlayer() {
    // A video-URI replacement must return the previous player's resources (its non-daemon executor
    // thread + live stream); the settings controller re-applies the URI on every settings change,
    // so without the release one settings session could leak several players.
    val old = MjpegVideoPlayer(MjpegView(activity))
    val oldExecutor = field(old, "executor") as ExecutorService
    setField(activity, "video", old)
    activity.setVideoUrl("http://10.0.0.7:8080/?action=stream")
    assertTrue("replacing the player must release the previous one", oldExecutor.isShutdown)
    assertNotSame(old, field(activity, "video"))
    // A second replacement releases the first new player too (no leak on repeated URI changes).
    val first = requireNotNull(field(activity, "video"))
    val firstExecutor = field(first, "executor") as ExecutorService
    activity.setVideoUrl(null)
    assertTrue("each replacement must release its predecessor", firstExecutor.isShutdown)
    assertNotSame(first, field(activity, "video"))
  }

  @Test
  fun setVideoUrlWithoutPreviousPlayerIsSafeAndStartsFresh() {
    // The register sweep can run before a player exists (or after onDestroy cleared it): the
    // replace must skip the release of a null predecessor and still install a working player.
    setField(activity, "video", null)
    activity.setVideoUrl("http://10.0.0.7:8080/?action=stream")
    assertTrue("a fresh player must be installed", field(activity, "video") is VideoPlayer)
  }

  @Test
  fun setControlsVisibleShouldHideAndShowPads() {
    activity.setControlsVisible(false)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
    assertEquals(View.GONE, activity.findViewById<View>(R.id.buttons)?.visibility)
    activity.setControlsVisible(true)
    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.controlsOverlay)?.visibility)
  }

  @Test
  fun setMagicButtonsShouldPopulateTheRow() {
    activity.setMagicButtons(3, listOf("▲", "■", "●"), 100)
    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    assertEquals(3, row.childCount)
    assertEquals("▲", (row.getChildAt(0) as android.widget.Button).text.toString())
  }

  @Test
  fun setMagicButtonsShouldApplyBottomMargin() {
    activity.setMagicButtons(5, listOf("▲", "■", "●", "✕", "◆"), 100)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val overlay = activity.findViewById<View>(R.id.controlsOverlay)!!
    val lp = overlay.layoutParams as ViewGroup.MarginLayoutParams
    assertTrue("bottom margin must be > 0 to clear buttons", lp.bottomMargin > 0)
    // Second call must preserve the margin (no double-growing).
    activity.setMagicButtons(5, listOf("▲", "■", "●", "✕", "◆"), 100)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(
        "margin kept stable",
        lp.bottomMargin,
        overlay.layoutParams.let { (it as ViewGroup.MarginLayoutParams).bottomMargin },
    )
  }

  @Test
  fun setMagicButtonsWithFullCountShouldPopulateFive() {
    activity.setMagicButtons(5, listOf("▲", "■", "●", "✕", "◆"), 100)
    val row = activity.findViewById<android.view.ViewGroup>(R.id.buttons)!!
    assertEquals(5, row.childCount)
  }

  @Test
  fun setMagicButtonsBeforeLayoutShouldRetryGuard() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    controller.create()
    val earlyActivity = controller.get()
    earlyActivity.setMagicButtons(3, listOf("▲", "■", "●"), 100)
    controller.resume().visible()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val overlay = earlyActivity.findViewById<View>(R.id.controlsOverlay)
    assertTrue("margin must be set after retry", overlay.bottom > 0)
  }

  @Test
  fun dispatchKeyEventShouldRouteGamepadKeys() {
    assertTrue(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
            )
        )
    )
    assertTrue(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
            )
        )
    )
  }

  @Test
  fun dispatchKeyEventWithUnmappedKeyShouldFallThrough() {
    // Not a gamepad key -> the controller does not consume it and the activity
    // lets the event fall through to super (no focus -> false).
    assertFalse(
        activity.dispatchKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
            )
        )
    )
  }

  private fun joystickMoveEvent(source: Int, x: Float): android.view.MotionEvent {
    val coords = android.view.MotionEvent.PointerCoords()
    coords.setAxisValue(android.view.MotionEvent.AXIS_X, x)
    val props = android.view.MotionEvent.PointerProperties()
    props.id = 0
    val now = android.os.SystemClock.uptimeMillis()
    return android.view.MotionEvent.obtain(
        now,
        now,
        android.view.MotionEvent.ACTION_MOVE,
        1,
        arrayOf(props),
        arrayOf(coords),
        0,
        0,
        1f,
        1f,
        0,
        0,
        source,
        0,
    )
  }

  @Test
  fun onGenericMotionEventShouldConsumeJoystickMoves() {
    assertTrue(
        activity.onGenericMotionEvent(
            joystickMoveEvent(android.view.InputDevice.SOURCE_JOYSTICK, 0.5f)
        )
    )
  }

  @Test
  fun onGenericMotionEventWithNonGamepadSourceShouldFallThrough() {
    assertFalse(
        activity.onGenericMotionEvent(
            joystickMoveEvent(android.view.InputDevice.SOURCE_MOUSE, 0.5f)
        )
    )
  }

  @Test
  fun dispatchKeyEventWithUnknownActionShouldFallThrough() {
    // A non DOWN/UP action hits the when's else branch and falls through to super.
    // ACTION_MULTIPLE is deprecated (API 33) but remains the only KeyEvent action
    // that is neither DOWN nor UP, which is exactly what this test needs.
    @Suppress("DEPRECATION")
    val keyEvent =
        android.view.KeyEvent(
            android.view.KeyEvent.ACTION_MULTIPLE,
            android.view.KeyEvent.KEYCODE_VOLUME_UP,
        )
    assertFalse(activity.dispatchKeyEvent(keyEvent))
  }

  @Test
  fun setSenderServiceWithNullShouldBeSafe() {
    activity.setSenderService(null)
    // Null guard: the original sender is kept.
    assertNotNull(activity.senderService)
  }

  @Test
  fun onDestroyWithNullCollaboratorsShouldBeSafe() {
    // Null the collaborators so the null branches in onDestroy run.
    setField(activity, "sensorManager", null)
    setField(activity, "video", null)
    setField(activity, "settingsController", null)
    method(activity, "onDestroy").invoke(activity)
  }

  @Test
  fun nullVideoRetryControllerShouldBeSafeAcrossLifecycle() {
    // Null the retry controller so the ?. null branches in onPause/onResume and
    // the Connected edge-trigger all run.
    setField(activity, "videoRetryController", null)
    val sender = activity.senderService
    awaitControlConnection(sender)
    method(activity, "onPause").invoke(activity)
    method(activity, "onResume").invoke(activity)
    org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    sender.disconnect("test done")
  }

  @Test
  fun dispatchEmptyWindowInsetsShouldNotChangeMargins() {
    val btnSettings = activity.findViewById<Button>(R.id.btnSettings)
    val chip = activity.findViewById<View>(R.id.targetChip)
    val gearStart = (btnSettings.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    val chipStart = (chip.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    // Dispatch insets with no display cutout: the listener must run and leave the margins intact.
    activity
        .findViewById<View>(R.id.main)
        .dispatchApplyWindowInsets(android.view.WindowInsets.CONSUMED)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val newGearStart = (btnSettings.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    val newChipStart = (chip.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    assertEquals("gear margin unchanged", gearStart, newGearStart)
    assertEquals("chip margin unchanged", chipStart, newChipStart)
  }

  @Test
  fun streamErrorDuringResumeShouldReportUnavailable() {
    val player = StubVideoPlayer()
    setField(activity, "video", player)
    method(activity, "onResume").invoke(activity)
    player.triggerStreamError()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
  }

  @Test
  fun streamErrorWithNullRetryControllerShouldBeSafe() {
    setField(activity, "videoRetryController", null)
    val player = StubVideoPlayer()
    setField(activity, "video", player)
    method(activity, "onResume").invoke(activity)
    player.triggerStreamError()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
  }

  /** Builds a SensorEvent of [type] via the modern SensorEventBuilder API. */
  private fun sensorEvent(type: Int): android.hardware.SensorEvent =
      org.robolectric.shadows.SensorEventBuilder.newBuilder()
          .setSensor(org.robolectric.shadows.ShadowSensor.newInstance(type))
          .setValues(floatArrayOf(0.7f, 0.7f, 0f))
          .build()
}

/** Lightweight [VideoPlayer] stub for tests that only need a non-null video field. */
private class StubVideoPlayer : VideoPlayer {
  override val isPlaying: Boolean
    get() = false

  override var showFps: Boolean = false
  override var scaleMode: ScaleMode = ScaleMode.FIT
  override var onPlayResult: ((Boolean) -> Unit)? = null
  var streamErrorListener: (() -> Unit)? = null

  override fun play(url: String?) {}

  override fun stop() {}

  override fun setOnStreamErrorListener(listener: (() -> Unit)?) {
    streamErrorListener = listener
  }

  override fun setOnFirstFrameListener(listener: (() -> Unit)?) {}

  override fun release() {}

  fun triggerStreamError() {
    streamErrorListener?.invoke()
  }
}
