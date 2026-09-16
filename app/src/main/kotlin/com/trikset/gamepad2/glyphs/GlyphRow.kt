package com.trikset.gamepad2.glyphs

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * A horizontal row of [GlyphButton]s that share ONE target ink height and ONE visual center line:
 * every cell gets the same fixed size, and each glyph's text size is derived from its own
 * [GlyphMetrics.visualHeightEm] so different glyphs (the digits 1/2, the eye, the usb icon) render
 * at the same VISUAL height - the "glyphs in a row must be visually aligned" rule (DECISIONS.md
 * "Smart glyph alignment"). The 90%-of-ink-in-the-same-rows criterion is the suggested way to CHECK
 * the alignment on-device (scripts/measure_glyph_row.py); the view guarantees it by construction
 * from the em-relative metrics.
 *
 * Cells are configured via [Item] (glyph, contentDescription); the tap action is per-cell
 * [onClick]. Use for the video-source preset chips (Settings > Video) and anywhere else a row of
 * tappable glyphs must stay aligned.
 *
 * Baseline alignment is disabled ([baselineAligned] = false): a horizontal LinearLayout otherwise
 * shifts equal-height cells vertically to line up their text baselines, so the cells' full-size
 * ring backgrounds end up on different top lines (the chips row "circles not in a row" report,
 * 2026-09-05, visible when per-glyph centering offsets differ, e.g. the OFF mode). Top-aligned
 * cells keep every ring on one shared line.
 */
class GlyphRow
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

  data class Item(val glyph: String, val contentDescription: String)

  /**
   * Builds [items] as equal fixed-size cells, each glyph rendered at [targetVisualHeightPx] of
   * visual ink height (e.g. the reference glyph "1" measured on-device). [cellSizePx] is the square
   * cell edge (the touch target); [gapPx] the inter-cell margin. [recenter] is forwarded to each
   * cell's [GlyphButton.renderGlyph] (see [GlyphRendering.render]).
   *
   * [chrome] applies per-cell visual chrome to each freshly created [GlyphButton] BEFORE its glyph
   * renders: the caller may set a background or text color here. Order matters — applying a
   * background AFTER the render would wipe the glyph's asymmetric ink-centering padding with the
   * drawable's intrinsic padding (an inset ring clobbers it with the uniform inset; hit
   * 2026-09-04), while chrome-before-render leaves the render's padding applied last.
   *
   * The cell's layoutParams are assigned BEFORE rendering so the shared fit-cap in
   * [GlyphRendering.render] sees a fixed view height (rendering into a height-less cell skipped the
   * cap, so at large font scales a row glyph could size past its cell and clip).
   */
  fun populate(
      items: List<Item>,
      targetVisualHeightPx: Float,
      cellSizePx: Int,
      gapPx: Int,
      recenter: Boolean = false,
      chrome: (GlyphButton) -> Unit = {},
      onClick: (Int) -> Unit,
  ) {
    orientation = HORIZONTAL
    // Equal fixed cells must share one top line: with baseline alignment a LinearLayout shifts
    // each cell vertically to line up its text baseline, staggering the full-cell ring
    // backgrounds (see the class doc — hit 2026-09-05).
    isBaselineAligned = false
    removeAllViews()
    items.forEachIndexed { index, item ->
      val button =
          GlyphButton(context).apply {
            contentDescription = item.contentDescription
            // Fixed cell size BEFORE rendering: the shared fit-cap in GlyphRendering.render reads
            // layoutParams.height, so a cap-skip here would let a row glyph size past its cell at
            // large font scales and clip (the chips "invisible glyph" report).
            layoutParams = ViewGroup.MarginLayoutParams(cellSizePx, cellSizePx)
            chrome(this)
            renderGlyph(item.glyph, targetVisualHeightPx, recenter)
            setOnClickListener { onClick(index) }
          }
      // Attach with the cell LayoutParams, then re-apply the margins the LinearLayout stores on
      // the child (the inter-cell gap; mirrored from the proven two-step add below).
      addView(button, ViewGroup.LayoutParams(cellSizePx, cellSizePx))
      val lp = button.layoutParams as MarginLayoutParams
      if (index > 0) lp.marginStart = gapPx
      button.layoutParams = lp
    }
  }
}
