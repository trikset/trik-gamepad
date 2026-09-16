package com.trikset.gamepad2.glyphs

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.trikset.gamepad2.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared rendering core for every centered bundled-glyph (the pill ⏻/↺, the gear ⚙, the magic
 * buttons, the video-source preset chips). One code path owns the three things that make a glyph
 * read as "placed in a row":
 *
 * * the bundled [R.font.symbols_mono] typeface + `includeFontPadding=false` (only the glyph's real
 *   ink box counts — the font's internal ascent/descent padding would offset everything);
 * * per-glyph **size equalization** from [GlyphMetrics]: `textSize = targetVisualHeightPx /
 *   visualHeightEm`, so a row of different glyphs all render at the SAME visual ink height (the
 *   magic buttons' ▲ and the chips' eye look the same size despite very different em-fills);
 * * ink **centering via asymmetric padding, never a translation** (moving the whole view would
 *   shift its background/ring off-center — regressions 2026-08-15 and 2026-09-03). The smart "Smart
 *   glyph alignment" mode (recenter=true) pads the ink-box offset measured from the paint at render
 *   time; the OFF baseline mode (recenter=false) applies no padding and leaves the glyph where
 *   default centering puts it.
 *
 * Consumers are thin [GlyphTextView] / [GlyphButton] views that just forward to [render]; a
 * [GlyphRow] composes them into an equalized, aligned row. This core is deliberately Every centered
 * glyph in the app renders through this core (see DECISIONS.md "Smart glyph alignment").
 */
object GlyphRendering {

  /** Bundled symbol font (the single typeface for all centered glyphs). */
  fun typeface(context: Context): Typeface =
      ResourcesCompat.getFont(context, R.font.symbols_mono) ?: Typeface.MONOSPACE

  /**
   * Applies the bundled typeface, drops the font's internal line padding and centers the content;
   * the shared pre-conditions for [render] / [centerExisting].
   */
  fun configure(view: TextView) {
    view.typeface = typeface(view.context)
    view.includeFontPadding = false
    view.gravity = Gravity.CENTER
  }

  /**
   * Renders [glyph] sized so its VISUAL ink height equals [targetVisualHeightPx], then centers it.
   * For a bundled glyph the metric's [GlyphMetrics.visualHeightEm] yields the text size; for an
   * unknown glyph (user-typed magic symbol) [DEFAULT_VISUAL_HEIGHT_EM] is assumed.
   *
   * The text size is capped so the glyph's FULL ink box plus the centering padding fit the view at
   * ANY font scale (TextView multiplies the set px text size by the user's font scale, so the cap
   * divides by it): the 90%-mass band under-reports a glyph with a thin tail (a triangle's point
   * carries little mass, so its band is ~0.45em while its box is ~0.6em) and a band-equalized size
   * alone would clip such glyphs (hit 2026-09-01: ▲ rendered zero ink, ■/● clipped to the lower
   * half).
   *
   * [recenter] selects the centering (see the "Smart glyph alignment" setting, pref_app.xml): false
   * = the OFF baseline look — the glyph stays where default gravity centering puts it (no ink
   * correction, no padding); true = the glyph's actual runtime ink-box center, measured from the
   * paint at render time, is aimed at the view center via asymmetric padding. The ring/chrome (the
   * view's background) covers the padded area, so only the ink moves (a translation would move the
   * background too — hit 2026-08-15 and 2026-09-03); [recenter] only picks which centering is used,
   * never the vehicle.
   */
  fun render(
      view: TextView,
      glyph: String,
      targetVisualHeightPx: Float,
      recenter: Boolean = false,
  ) {
    view.text = glyph
    configure(view)
    val metric = GlyphMetrics.metricFor(glyph)
    val visualHeightEm = metric?.visualHeightEm ?: DEFAULT_VISUAL_HEIGHT_EM
    var textSizePx = targetVisualHeightPx / visualHeightEm
    val viewHeight = view.layoutParams?.height
    if (metric != null && viewHeight != null && viewHeight > 0) {
      // The glyph must fit the fixed view at ANY font scale. Android lays out the LINE BOX
      // (ascent + descent), not the ink box, and a line box taller than the view clips even
      // when the ink alone would fit. So cap at viewHeight / (fontScale * (LINE_BOX_EM +
      // 2*reserveEm)): line box + the 2*|offset| centering padding <= viewHeight. The reserve is
      // the ink-box offset actually applied by [center] (measured here — bounds scale linearly,
      // so the em value is size-invariant) in the smart (ON) mode; the OFF baseline mode pads
      // nothing, so its reserve is 0 and only the line box must fit. The 90%-mass band alone
      // under-reports glyphs with thin tails (▲ renders zero ink, ■/● clip - hit 2026-09-01).
      val fontScale = view.resources.configuration.fontScale
      val reserveEm =
          if (recenter) {
            view.paint.textSize = textSizePx
            val fitBounds = Rect()
            view.paint.getTextBounds(glyph, 0, glyph.length, fitBounds)
            val fitMetrics = view.paint.fontMetrics
            abs(
                    (fitBounds.top + fitBounds.bottom) / 2f -
                        (fitMetrics.ascent + fitMetrics.descent) / 2f
                )
                .div(textSizePx)
          } else {
            0f
          }
      val maxFit = viewHeight / (fontScale * (GlyphMetrics.LINE_BOX_EM + 2 * reserveEm))
      if (textSizePx > maxFit) textSizePx = maxFit
    }
    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
    center(view, recenter)
  }

  /**
   * Centers the glyph already set on [view] at its current text size (pill / gear / error text).
   * [recenter] has the same meaning as in [render]; note the pill/gear chrome uses XML padding and
   * never goes through this method, so only glyph-tile consumers use it today.
   */
  fun centerExisting(view: TextView, recenter: Boolean = false) {
    configure(view)
    center(view, recenter)
  }

  /**
   * Positions the glyph content: the smart (ON) mode aims the glyph's ink-box center, measured from
   * the paint at render time, at the view center; the OFF baseline mode leaves the glyph exactly
   * where default gravity-CENTER places it and clears any padding from an earlier render.
   *
   * The offset is ALWAYS cancelled as asymmetric [android.view.View.setPaddingRelative] — never a
   * view translation, which moves the glyph together with its own background and shifts the circle
   * off-center instead of the ink (the magic-button regression from 2026-08-15 and again on
   * 2026-09-03, when [recenter] was first wired as a translation). The ring/background covers the
   * padded area, so padding moves only the ink.
   */
  private fun center(view: TextView, recenter: Boolean) {
    if (!recenter) {
      view.setPaddingRelative(0, 0, 0, 0)
      return
    }
    val paint = view.paint
    val text = view.text.toString()
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fm = paint.fontMetrics
    // Line-box center vs ink center, both relative to the baseline (paint metrics: ascent
    // negative, descent positive).
    val lineCenter = (fm.ascent + fm.descent) / 2f
    val inkCenterY = (bounds.top + bounds.bottom) / 2f
    val inkCenterX = (bounds.left + bounds.right) / 2f
    val offsetY = inkCenterY - lineCenter
    val offsetX = inkCenterX - (paint.measureText(text) / 2f)
    // Round to the nearest int (not truncate): truncation leaves up to a full px of un-cancelled
    // offset, which a 0.5-tolerance centering test would see.
    val paddingTop = (-PADDING_FACTOR * offsetY).coerceAtLeast(0f).roundToInt()
    val paddingBottom = (PADDING_FACTOR * offsetY).coerceAtLeast(0f).roundToInt()
    val paddingStart = (-PADDING_FACTOR * offsetX).coerceAtLeast(0f).roundToInt()
    val paddingEnd = (PADDING_FACTOR * offsetX).coerceAtLeast(0f).roundToInt()
    view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
  }

  private const val PADDING_FACTOR = 2f

  /**
   * Visual-height/em assumed for a glyph absent from [GlyphMetrics] (user-typed magic symbols): a
   * typical bundled glyph fills ~70% of its em, so textSize = target / 0.7 keeps the row's
   * equalized height plausible for unknown symbols too.
   */
  const val DEFAULT_VISUAL_HEIGHT_EM = 0.7f
}
