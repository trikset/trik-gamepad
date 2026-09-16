package com.trikset.gamepad2

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionFeedbackTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()
  private val targetText = "192.168.77.1:4444"

  private fun feedback(
      statusTextProvider: () -> TextView?,
      targetProvider: () -> String? = { targetText },
      connectAction: () -> Unit = {},
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { null },
          rootViewProvider = { null },
          statusTextProvider = statusTextProvider,
          targetProvider = targetProvider,
          connectAction = connectAction,
      )

  /** Builds a feedback whose border paints a real [btn] (or skips when null). */
  private fun feedbackWith(
      btn: Button?,
      status: TextView?,
      targetProvider: () -> String? = { targetText },
  ): ConnectionFeedback =
      ConnectionFeedback(
          context = context,
          settingsButtonProvider = { btn },
          rootViewProvider = { null },
          statusTextProvider = { status },
          targetProvider = targetProvider,
          connectAction = {},
      )

  @Test
  fun updateShouldBeSafeWhenButtonMissing() {
    val feedback = feedback({ null })
    feedback.update(ConnectionState.Connected)
    feedback.error("boom")
  }

  /** Builds a feedback wired to a real [MainActivity]'s gear/pill/root views. */
  private fun activityFeedback(activity: MainActivity): ConnectionFeedback =
      ConnectionFeedback(
          context = activity,
          settingsButtonProvider = { activity.findViewById(R.id.btnSettings) },
          rootViewProvider = { activity.findViewById(R.id.main) },
          statusTextProvider = { activity.findViewById(R.id.connectionStatus) },
          targetProvider = { targetText },
          connectAction = {},
      )

  @Test
  fun errorShouldShowGlassPillWithMessage() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val feedback = activityFeedback(activity)
    feedback.error("Connection to 192.0.2.1:4444 error.")
    val pill = activity.findViewById<TextView>(R.id.connectionError)
    assertEquals(View.VISIBLE, pill.visibility)
    assertEquals("Connection to 192.0.2.1:4444 error.", pill.text.toString())
    assertEquals("Connection to 192.0.2.1:4444 error.", pill.contentDescription.toString())
  }

  @Test
  fun updateShouldBeSafeWhenBackgroundIsNotLayerDrawable() {
    feedbackWith(Button(context), null).update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldBeSafeWhenLayerHasNoSettingsBackground() {
    // A LayerDrawable without the @+id/settingsButtonBg layer -> the shape lookup returns
    // null and update must no-op (the gear keeps its XML stroke color).
    val btn = Button(context)
    btn.background =
        android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable()))
    feedbackWith(btn, null).update(ConnectionState.Connected)
  }

  @Test
  fun updateShouldPaintGearBorder() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val feedback = activityFeedback(activity)
    val app = org.robolectric.RuntimeEnvironment.getApplication()
    feedback.update(ConnectionState.Connected)
    assertEquals(app.getColor(R.color.hud_accent_connected_dark), borderStrokeColor(activity))
    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals(app.getColor(R.color.hud_accent_error), borderStrokeColor(activity))
    feedback.update(ConnectionState.Connecting)
    assertEquals(app.getColor(R.color.hud_accent_connecting), borderStrokeColor(activity))
  }

  @Test
  fun updateShouldMakeGearDescriptionStateAware() {
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    val feedback = feedbackWith(btn, status)

    feedback.update(ConnectionState.Connecting)
    assertEquals("Connecting. Toggle system bars", btn.contentDescription.toString())
    feedback.update(ConnectionState.Connected)
    assertEquals("Connected. Toggle system bars", btn.contentDescription.toString())
    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals("Disconnected. Toggle system bars", btn.contentDescription.toString())
  }

  @Test
  fun updateShouldNotAnnounceWhenAccessibilityDisabled() {
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    val feedback = feedbackWith(btn, status)

    feedback.update(ConnectionState.Connecting)
    // announceForAccessibility is a no-op when no accessibility service is running; the
    // announcement decision itself is covered by ConnectionAnnouncerTest.
    assertEquals(null, lastAnnouncement())
  }

  private fun announcementEvents(): List<android.view.accessibility.AccessibilityEvent> {
    val manager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
    return org.robolectric.Shadows.shadowOf(manager).sentAccessibilityEvents.filter {
      // TYPE_ANNOUNCEMENT (deprecated API 33) is the only way to recognize an
      // announceForAccessibility announcement in a sent-event list.
      @Suppress("DEPRECATION")
      it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
    }
  }

  private fun lastAnnouncement(): CharSequence? =
      announcementEvents().lastOrNull()?.text?.lastOrNull()

  @Test
  fun updateShouldRenderConnectionStatusIcon() {
    // update() returns early when the settings button is missing, so use a real
    // button with the gear background (border + status render together).
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    val feedback = feedbackWith(btn, status)

    feedback.update(ConnectionState.Connecting)
    // Symbol-only HUD: the pill shows the connecting glyph; the spoken text is the
    // contentDescription (the robot chip shows the host visually).
    assertEquals("↺", status.text.toString())
    assertEquals("Connecting to 192.168.77.1:4444…", status.contentDescription.toString())
    assertEquals(View.VISIBLE, status.visibility)

    feedback.update(ConnectionState.Connected)
    // Connected -> the pill is hidden; the video / gear border conveys the state.
    assertEquals(View.GONE, status.visibility)

    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals("⏻", status.text.toString())
    assertEquals("Tap to connect…", status.contentDescription.toString())
    assertEquals(View.VISIBLE, status.visibility)
  }

  @Test
  fun updateWithNoConfiguredTargetShouldHidePillWhenDisconnected() {
    val btn = Button(context)
    btn.setBackgroundResource(R.drawable.btn_settings)
    val status = TextView(context)
    val feedback = feedbackWith(btn, status, targetProvider = { null })

    feedback.update(ConnectionState.Connecting)
    assertEquals(View.VISIBLE, status.visibility)

    feedback.update(ConnectionState.Connected)
    assertEquals(View.GONE, status.visibility)

    // No target -> no "tap to connect" affordance (a video-only device has nothing to connect to).
    feedback.update(ConnectionState.Disconnected("x"))
    assertEquals(View.GONE, status.visibility)
  }

  @Test
  fun attachShouldWireTapToConnect() {
    val status = TextView(context)
    var connected = false
    val feedback = feedback({ status }, connectAction = { connected = true })

    feedback.attach()
    status.performClick()
    assertTrue("tapping the status line must trigger the connect action", connected)
  }

  @Test
  fun attachShouldBeSafeWithoutStatusView() {
    feedback({ null }).attach()
    // No crash when the status line is not in the hierarchy.
  }

  private fun borderStrokeColor(activity: MainActivity): Int {
    val btn = activity.findViewById<Button>(R.id.btnSettings)!!
    val bg = btn.background as android.graphics.drawable.LayerDrawable
    val shape =
        bg.findDrawableByLayerId(R.id.settingsButtonBg)
            as android.graphics.drawable.GradientDrawable
    return org.robolectric.Shadows.shadowOf(shape).strokeColor
  }
}
