package com.trikset.gamepad2.glyphs

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * A [TextView] that renders a bundled glyph through [GlyphRendering]: the symbols_mono typeface, no
 * font-line padding, a text size that makes the glyph's VISUAL ink height equal to the row's
 * target, and ink centering via asymmetric padding. AppCompatTextView so the theme/tint pipeline
 * matches the rest of the HUD (lint AppCompatCustomView). Every centered glyph in the app should be
 * a [GlyphTextView] (or a [GlyphButton] where a tappable surface is needed) so the whole HUD reads
 * one visual alignment (see DECISIONS.md "Smart glyph alignment").
 */
class GlyphTextView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

  /**
   * Sets [glyph] sized to [targetVisualHeightPx] of visual ink height, then centers it. [recenter]
   * picks the centering: false = the plain baseline look (default centering, no ink correction),
   * true = aim the runtime ink box at the view center (see [GlyphRendering.render]). The chrome
   * ring/background is never moved — centering shifts only the ink.
   */
  fun renderGlyph(glyph: String, targetVisualHeightPx: Float, recenter: Boolean = false) {
    GlyphRendering.render(this, glyph, targetVisualHeightPx, recenter)
  }

  /**
   * Centers the already-set text at its current size (single-glyph pill / gear). [recenter] has the
   * same meaning as in [render] (see [GlyphRendering.centerExisting]).
   */
  fun centerExistingGlyph(recenter: Boolean = false) {
    GlyphRendering.centerExisting(this, recenter)
  }
}
