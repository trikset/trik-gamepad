package com.trikset.gamepad2

import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import com.trikset.gamepad2.glyphs.GlyphButton
import com.trikset.gamepad2.glyphs.GlyphMetrics
import com.trikset.gamepad2.glyphs.GlyphRendering

/**
 * Builds the magic buttons (`1..count`) into a container and sends `btn N down` with haptic
 * feedback on tap. The button's [Button.text] is the display glyph from [symbols] (cosmetic — the
 * protocol command stays numeric); accessibility announces "Button N". Extracted from MainActivity
 * so the button construction and command mapping are directly testable without reflection.
 *
 * Each button is a [GlyphButton]: the bundled mono symbol font (monospace, so the circle-diameter
 * sizing and ink centering stay exact), sized so every glyph's VISUAL ink height equals 60% of the
 * circle diameter (MAGIC_GLYPH_SIZE_RATIO) via its [GlyphMetrics] - different glyphs (▲ ■ ● ✕ ◆,
 * digits, letters) render at the same visual height instead of the same em size. Ink centering is
 * shared asymmetric-padding math in [GlyphRendering]; a user-typed symbol absent from the metrics
 * table falls back to runtime paint-based centering.
 */
class MagicButtonPanel(
    private val context: Context,
    private val send: (String) -> Unit,
) {
  private var container: ViewGroup? = null

  /**
   * Builds the buttons into [container]. [recenter] picks the glyph centering (the "Smart glyph
   * alignment" setting; see [GlyphRendering.render]): false = the plain baseline look (default
   * centering, no ink correction); true = aim the runtime ink box at the circle center.
   * [sizePercent] scales the button from the WCAG-minimum 48dp touch target (70-150%).
   */
  fun populate(
      container: ViewGroup,
      count: Int,
      symbols: List<String>,
      recenter: Boolean = false,
      sizePercent: Int = SettingsFragment.DEFAULT_MAGIC_BUTTON_SIZE,
  ) {
    this.container = container
    container.removeAllViews()
    val baseTouchTarget = context.resources.getDimensionPixelSize(R.dimen.touch_target_min)
    val scale = sizePercent.toFloat() / SettingsFragment.MAGIC_BUTTON_SIZE_PERCENT_UNIT
    val touchTarget = (baseTouchTarget * scale).toInt()
    val margin = context.resources.getDimensionPixelSize(R.dimen.hud_magic_button_margin_start)
    val targetVisualHeight = touchTarget * MAGIC_GLYPH_SIZE_RATIO
    for (num in 1..count) {
      val name = num.toString()
      val glyph = symbols.getOrNull(num - 1) ?: name
      val btn =
          GlyphButton(context).apply {
            isHapticFeedbackEnabled = true
            // Fixed square bounds (48dp) so the oval background renders as a perfect circle: an
            // `oval` drawable stretches to the view bounds, so WRAP_CONTENT + the themed Button's
            // default horizontal padding made the buttons wider than tall (ellipses). Zero the
            // theme padding so nothing pushes the content wider than the square.
            layoutParams =
                ViewGroup.MarginLayoutParams(touchTarget, touchTarget).apply {
                  if (num > 1) marginStart = margin
                }
            setPadding(0, 0, 0, 0)
            setSingleLine(true)
            // Size + ink-center via the shared core: visual height = 60% of the circle diameter
            // (pictograms, sized in px so the ratio holds regardless of the system font scale).
            renderGlyph(glyph, targetVisualHeight, recenter)
            // Explicit light text on the dark fills (contrast verified by WcagContrastTest);
            // setAccent recolors the glyph to the connection-state accent afterwards.
            setTextColor(ContextCompat.getColor(context, R.color.magic_button_text))
            // Accessibility: the glyph is part of the description so a screen-reader user can map
            // the symbol to its meaning ("Button 1 · ▲"), not just its index.
            contentDescription = context.getString(R.string.button_number_description, name, glyph)
            setBackgroundResource(R.drawable.hud_button_circle)
            setOnClickListener {
              send("btn $name down")
              // One strong pulse per tap (LONG_PRESS -> EFFECT_HEAVY_CLICK), user-chosen "one
              // strong for button". Respects the system haptics setting.
              this.haptic(Haptics.Level.HEAVY)
            }
          }
      container.addView(btn)
    }
  }

  fun clearListeners(container: ViewGroup) {
    for (i in 0 until container.childCount) {
      container.getChildAt(i).setOnClickListener(null)
    }
  }

  /**
   * Recolors the magic-button glyphs to the connection-state accent (green/amber/sepia/red — see
   * [ConnectionIndicator]). Only the glyph color changes; the circular glass background, count and
   * haptics are untouched.
   */
  fun setAccent(@androidx.annotation.ColorRes colorRes: Int) {
    val accent = ContextCompat.getColor(context, colorRes)
    val views = container
    if (views == null) return
    for (i in 0 until views.childCount) {
      (views.getChildAt(i) as? Button)?.setTextColor(accent)
    }
  }

  private companion object {
    // Magic-button glyph VISUAL height as a fraction of the circle diameter (60%). The shared
    // GlyphRendering converts this to a per-glyph text size via GlyphMetrics.visualHeightEm.
    const val MAGIC_GLYPH_SIZE_RATIO = 0.6f
  }
}
