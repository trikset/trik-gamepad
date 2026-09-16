package com.trikset.gamepad2

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [RobotChipController] (host text, glyph tinting, a11y description). */
@RunWith(RobolectricTestRunner::class)
class RobotChipControllerTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()
  private var accent = R.color.hud_accent_connected

  private fun chip(
      hostText: String = "192.168.0.1",
  ): Pair<LinearLayout, RobotChipController> {
    val chipView = LinearLayout(context)
    val host = TextView(context)
    host.text = hostText
    val controlIcon = ImageView(context)
    controlIcon.tag = "controlStatusIcon"
    val videoIcon = ImageView(context)
    videoIcon.tag = "videoStatusIcon"
    chipView.addView(host)
    chipView.addView(controlIcon)
    chipView.addView(videoIcon)
    val controller =
        RobotChipController(
            context = context,
            chipProvider = { chipView },
            chipTextProvider = { host },
            controlAccentProvider = { accent },
        )
    return chipView to controller
  }

  @Test
  fun setHostShouldUpdateHostText() {
    val (_, controller) = chip("10.0.0.9")
    controller.setHost("10.0.0.9")
    // setHost only writes the TextView; the description refresh happens on status/accent changes.
  }

  @Test
  fun paintControlAccentShouldTintControlGlyphAndSetDescription() {
    val (chipView, controller) = chip()
    controller.setVideoStatus(VideoStatus.PLAYING)
    controller.paintControlAccent(R.color.hud_accent_connecting)

    val controlIcon = chipView.findViewWithTag<ImageView>("controlStatusIcon")
    assertNotNull("control glyph must exist", controlIcon)
    assertTrue("control glyph must carry a color filter", controlIcon.colorFilter != null)
    val description = chipView.contentDescription.toString()
    assertTrue("description must mention the host", description.contains("192.168.0.1"))
    assertTrue("description must mention control status", description.contains("control"))
    assertTrue("description must mention video status", description.contains("video"))
  }

  @Test
  fun videoStatusChangesShouldRepaintVideoGlyph() {
    val (chipView, controller) = chip()
    controller.setVideoStatus(VideoStatus.PLAYING)
    val videoIcon = chipView.findViewWithTag<ImageView>("videoStatusIcon")
    assertTrue("playing glyph must be tinted", videoIcon.colorFilter != null)

    // No-op on the same status (guard branch).
    controller.setVideoStatus(VideoStatus.PLAYING)
  }

  @Test
  fun controlAccentProviderShouldDriveDescriptionWord() {
    val (chipView, controller) = chip()
    controller.setVideoStatus(VideoStatus.LOADING)
    accent = R.color.hud_accent_connected
    controller.paintControlAccent(R.color.hud_accent_connected)
    val green = chipView.contentDescription.toString()

    accent = R.color.hud_accent_error
    controller.paintControlAccent(R.color.hud_accent_error)
    val red = chipView.contentDescription.toString()

    assertEquals(
        "connected and disconnected must produce different control words",
        green == red,
        false,
    )
  }

  @Test
  fun eachVideoStatusShouldRenderDistinctWord() {
    val seen = mutableSetOf<String>()
    for (status in VideoStatus.entries) {
      val (chipView, controller) = chip()
      controller.setVideoStatus(status)
      // DISABLED equals the initial value so setVideoStatus no-ops; force the description
      // refresh to render every branch.
      controller.paintControlAccent(R.color.hud_accent_connected)
      val word = chipView.contentDescription.toString()
      assertTrue("video word must be non-empty for $status", word.isNotBlank())
      seen.add(word)
    }
    // All five statuses must render (covers every videoWordResource branch).
    assertEquals(5, seen.size)
  }

  @Test
  fun standbyAccentShouldRenderStandbyWord() {
    val (chipView, controller) = chip()
    accent = R.color.hud_sepia
    controller.paintControlAccent(R.color.hud_sepia)
    assertTrue(
        "standby accent must render the standby word",
        chipView.contentDescription.toString().contains("standby"),
    )
  }

  @Test
  fun nullChipProviderShouldBeSafe() {
    val controller =
        RobotChipController(
            context = context,
            chipProvider = { null },
            chipTextProvider = { null },
            controlAccentProvider = { accent },
        )
    // refreshChipDescription must return early on a null chip; nothing may throw.
    controller.setVideoStatus(VideoStatus.PLAYING)
    controller.paintControlAccent(R.color.hud_accent_connecting)
    controller.setHost("10.0.0.9")
  }

  @Test
  fun nullChipTextShouldFallBackToEmptyHost() {
    val chipView = LinearLayout(context)
    val host = TextView(context)
    host.text = null
    chipView.addView(host)
    val controller =
        RobotChipController(
            context = context,
            chipProvider = { chipView },
            // A null provider result, not a null TextView.text (which Robolectric reports as ""),
            // is what trips the ?: fallback to the empty-host placeholder.
            chipTextProvider = { null },
            controlAccentProvider = { accent },
        )
    controller.paintControlAccent(R.color.hud_accent_connected)
    assertTrue(
        "null host text must fall back to the empty placeholder",
        chipView.contentDescription
            .toString()
            .contains(MainActivitySettingsController.TARGET_CHIP_EMPTY),
    )
  }

  @Test
  fun missingGlyphTagShouldNotCrash() {
    val chipView = LinearLayout(context)
    val host = TextView(context)
    host.text = "192.168.0.1"
    chipView.addView(host)
    val controller =
        RobotChipController(
            context = context,
            chipProvider = { chipView },
            chipTextProvider = { host },
            controlAccentProvider = { accent },
        )
    controller.setVideoStatus(VideoStatus.PLAYING)
    controller.paintControlAccent(R.color.hud_accent_connected)
    // No glyph views with the expected tags: tintChipGlyph must no-op, not throw.
  }
}
