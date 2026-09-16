package com.trikset.gamepad2

import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Direct tests for [MagicButtonPanel]. */
@RunWith(RobolectricTestRunner::class)
class MagicButtonPanelTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()
  private val sent = ArrayList<String>()
  private val panel = MagicButtonPanel(context) { sent.add(it) }
  private val symbols = listOf("▲", "■", "●", "✕", "◆")

  @Test
  fun populateShouldCreateRequestedButtonsInOrder() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    assertEquals(3, container.childCount)
    val first = container.getChildAt(0) as Button
    val last = container.getChildAt(2) as Button
    assertEquals("▲", first.text.toString())
    assertEquals("●", last.text.toString())
  }

  @Test
  fun populateShouldReplaceExistingButtons() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)
    panel.populate(container, 4, symbols)

    assertEquals(4, container.childCount)
  }

  @Test
  fun populateWithZeroCountShouldCreateNoButtons() {
    val container = FrameLayout(context)
    panel.populate(container, 0, symbols)
    assertEquals(0, container.childCount)
  }

  @Test
  fun populateShouldSetAccessibleButtonDescriptions() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)

    assertEquals("Button 1 · ▲", (container.getChildAt(0) as Button).contentDescription)
    assertEquals("Button 2 · ■", (container.getChildAt(1) as Button).contentDescription)
  }

  @Test
  fun populateFallsBackToNumbersForMissingSymbols() {
    val container = FrameLayout(context)
    panel.populate(container, 3, listOf("▲"))

    assertEquals("▲", (container.getChildAt(0) as Button).text.toString())
    assertEquals("2", (container.getChildAt(1) as Button).text.toString())
    assertEquals("3", (container.getChildAt(2) as Button).text.toString())
  }

  @Test
  fun populateShouldSet48dpTouchTargets() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)
    val expected = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
    for (i in 0 until container.childCount) {
      val btn = container.getChildAt(i) as Button
      // The touch target is the fixed square layout params (48dp), not the theme's
      // minimumWidth (88dp Material default) — the circle drawable stretches to these bounds.
      val lp = btn.layoutParams as ViewGroup.MarginLayoutParams
      assertEquals("width must be 48dp", expected, lp.width)
      assertEquals("height must be 48dp", expected, lp.height)
    }
  }

  @Test
  fun populateShouldSetContrastSafeTextColor() {
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)
    val btn = container.getChildAt(0) as Button
    assertEquals(
        androidx.core.content.ContextCompat.getColor(context, R.color.magic_button_text),
        btn.currentTextColor,
    )
  }

  @Test
  fun centerGlyphShouldNotTranslateTheWholeButton() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    for (i in 0 until container.childCount) {
      val btn = container.getChildAt(i) as Button
      // The glyph centering must move only the ink (via padding), never the whole button:
      // a translation would shift the circular background off-center in the cluster and into
      // the cluster's clip area (regression guard — hit 2026-08-15).
      assertEquals("button $i translationX must stay 0", 0f, btn.translationX, 0f)
      assertEquals("button $i translationY must stay 0", 0f, btn.translationY, 0f)
    }
  }

  @Test
  fun baselineModeShouldLeavePlainCenteredTextWithoutPadding() {
    // "Smart glyph alignment" OFF = the plain baseline look: default gravity centering only, no
    // ink correction, so no asymmetric padding at all (the button itself must not move either).
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)
    val btn = container.getChildAt(0) as Button
    assertNeverTranslated(btn)
    assertEquals("baseline mode must not pad top", 0, btn.paddingTop)
    assertEquals("baseline mode must not pad bottom", 0, btn.paddingBottom)
    assertEquals("baseline mode must not pad start", 0, btn.paddingStart)
    assertEquals("baseline mode must not pad end", 0, btn.paddingEnd)
  }

  @Test
  fun recenterModeShouldCenterKnownGlyphByPaintInkBoxPadding() {
    // "Smart glyph alignment" ON: the glyph's runtime ink-box center is aimed at the view center,
    // but the vehicle stays asymmetric padding — the button (and its circular background) must not
    // move (regression guard: recenter was once wired as a translation and shifted the circle
    // instead of the ink — hit 2026-09-03).
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols, recenter = true)
    val btn = container.getChildAt(0) as Button
    assertNeverTranslated(btn)
    val metric = com.trikset.gamepad2.glyphs.GlyphMetrics.metricFor(btn.text.toString())
    assertNotNull("default glyph ▲ must have metrics", metric)
    val inkOffsetY = paintInkOffsetY(btn)
    assertCenteringPadding(btn, inkOffsetY)
    // The ink-box target must differ from the metric median for this glyph (▲ carries most of its
    // ink in its base, so its median sits well below its geometric box center) — otherwise the
    // padding assertion above would be vacuous.
    val medianOffsetY = -metric!!.medianBiasEm * btn.textSize
    assertTrue(
        "▲ ink-box offset must differ from its metric median offset",
        kotlin.math.abs(inkOffsetY - medianOffsetY) > 0.5f,
    )
  }

  @Test
  fun unknownGlyphShouldPadOnlyInSmartMode() {
    // A glyph absent from the metrics table (Ω) has no centering metadata: it centers on its paint
    // ink box in the smart (ON) mode and is left plain-centered (no padding) in the baseline (OFF)
    // mode.
    for (recenter in arrayOf(false, true)) {
      val container = FrameLayout(context)
      panel.populate(container, 1, listOf("Ω"), recenter = recenter)
      val btn = container.getChildAt(0) as Button
      assertNeverTranslated(btn)
      if (recenter) {
        assertCenteringPadding(btn, paintInkOffsetY(btn))
      } else {
        assertEquals("baseline mode must not pad top", 0, btn.paddingTop)
        assertEquals("baseline mode must not pad bottom", 0, btn.paddingBottom)
      }
    }
  }

  /** Paint ink-box center minus line-box center (px) — the fallback centering target. */
  private fun paintInkOffsetY(btn: Button): Float {
    val paint = btn.paint
    val text = btn.text.toString()
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    return (bounds.top + bounds.bottom) / 2f - (fm.ascent + fm.descent) / 2f
  }

  /** Asymmetric padding must cancel [offsetY] so the ink sits centered on the button (0.5px). */
  private fun assertCenteringPadding(btn: Button, offsetY: Float) {
    assertEquals(
        "vertical padding must cancel the ink offset",
        0f,
        btn.paddingTop - btn.paddingBottom + 2f * offsetY,
        0.5f,
    )
  }

  private fun assertNeverTranslated(btn: Button) {
    assertEquals("recenter must never translate the button", 0f, btn.translationX, 0f)
    assertEquals("recenter must never translate the button", 0f, btn.translationY, 0f)
  }

  @Test
  fun clickShouldSendBtnDownCommand() {
    val container = FrameLayout(context)
    panel.populate(container, 3, symbols)

    (container.getChildAt(1) as Button).performClick()

    // The command stays numeric even though the glyph is display-only.
    assertEquals(listOf("btn 2 down"), sent)
  }

  @Test
  fun clickShouldPerformHapticFeedback() {
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)

    val button = container.getChildAt(0) as Button
    // The haptic contract: the button is haptic-enabled and its click performs
    // the HEAVY (strong) feedback. (The old `isPressed || !isPressed` tautology asserted
    // nothing — hit 2026-08-11.)
    assertTrue(button.isHapticFeedbackEnabled)
    button.performClick()
    assertTrue(sent.isNotEmpty())
    assertEquals(
        "magic buttons must fire the strong pulse on tap",
        Haptics.constant(Haptics.Level.HEAVY),
        org.robolectric.Shadows.shadowOf(button).lastHapticFeedbackPerformed(),
    )
  }

  @Test
  fun clearListenersShouldRemoveClickHandlers() {
    val container = FrameLayout(context)
    panel.populate(container, 2, symbols)
    panel.clearListeners(container)

    (container.getChildAt(0) as Button).performClick()
    (container.getChildAt(1) as Button).performClick()
    assertTrue("no commands should be sent after clearListeners", sent.isEmpty())
  }

  @Test
  fun clearListenersShouldBeSafeOnEmptyContainer() {
    val container = FrameLayout(context)
    panel.clearListeners(container)
    // No crash and no buttons to wire.
    assertEquals(0, container.childCount)
  }

  @Test
  fun setAccentBeforePopulateShouldBeSafe() {
    // The container is null before populate -> setAccent takes the early-return path.
    panel.setAccent(R.color.hud_accent_connected)
    // After populate the accent must still apply.
    val container = FrameLayout(context)
    panel.populate(container, 1, symbols)
    panel.setAccent(R.color.hud_accent_connecting)
  }
}
