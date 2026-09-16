package com.trikset.gamepad2

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.trikset.gamepad2.glyphs.GlyphButton
import com.trikset.gamepad2.glyphs.GlyphMetrics
import com.trikset.gamepad2.glyphs.GlyphRendering
import com.trikset.gamepad2.glyphs.GlyphRow
import com.trikset.gamepad2.glyphs.GlyphTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Direct tests for the reusable glyph suite ([GlyphRendering] + the [GlyphTextView]/[GlyphButton]
 * views + [GlyphRow]).
 */
@RunWith(RobolectricTestRunner::class)
class GlyphViewsTest : RobolectricTestBase() {

  private val context = org.robolectric.RuntimeEnvironment.getApplication()

  @Test
  fun glyphTextViewRendersBundledGlyphCentered() {
    val view = GlyphTextView(context)
    view.renderGlyph("1", 20f)

    // Bundled font + no font-line padding + center gravity: the shared pre-conditions.
    assertEquals(GlyphRendering.typeface(context), view.typeface)
    assertTrue(view.includeFontPadding.not())
    assertEquals(android.view.Gravity.CENTER, view.gravity)
    assertEquals("1", view.text.toString())
    // Sized from the metric: textSize * visualHeightEm == target (within int rounding).
    val metric = GlyphMetrics.metricFor("1")
    assertNotNull(metric)
    assertEquals(20f, view.textSize * metric!!.visualHeightEm, 0.6f)
  }

  @Test
  fun glyphTextViewCentersExistingGlyphAtCurrentSize() {
    val view = GlyphTextView(context)
    view.text = "⏻"
    view.textSize = 24f
    view.centerExistingGlyph()
    // Configured (bundled font, no font padding, centered) without changing the text size.
    assertEquals("⏻", view.text.toString())
    assertEquals(24f, view.textSize, 0.01f)
    assertEquals(GlyphRendering.typeface(context), view.typeface)
  }

  @Test
  fun glyphButtonRendersAndIsTappable() {
    val button = GlyphButton(context)
    button.renderGlyph("■", 20f)
    assertEquals("■", button.text.toString())
    assertTrue(button.isEnabled)
  }

  @Test
  fun renderCapsTextSizeWhenGlyphWouldClip() {
    // A fixed 48dp-square cell is too small for the triangle's line box at an equalized size; the
    // cap must shrink the text size so the glyph stays inside the cell. Regression guard — hit
    // 2026-09-01: ▲ rendered zero ink, ■/● clipped. The plain OFF mode pads nothing, so only the
    // line box (no bias reserve) must fit.
    val view = GlyphTextView(context)
    view.layoutParams = ViewGroup.MarginLayoutParams(48, 48)
    view.renderGlyph("▲", 100f)

    val metric = GlyphMetrics.metricFor("▲")
    assertNotNull(metric)
    val uncapped = 100f / metric!!.visualHeightEm
    assertTrue(
        "cap must shrink text size below the uncapped equalized size",
        view.textSize < uncapped,
    )
    // The line box + padding must still fit the 48px cell at fontScale 1 (Robolectric default).
    val contentHeight =
        view.paddingTop + view.paddingBottom + view.textSize * GlyphMetrics.LINE_BOX_EM
    assertTrue("glyph content must fit the cell", contentHeight <= 48f + 1f)
  }

  @Test
  fun renderFallsBackToDefaultHeightForUnknownGlyphs() {
    val view = GlyphTextView(context)
    // Ω is absent from the metrics table: default 0.7 em assumed, no cap (no metric).
    view.renderGlyph("Ω", 20f)
    assertEquals("Ω", view.text.toString())
    assertEquals(20f / GlyphRendering.DEFAULT_VISUAL_HEIGHT_EM, view.textSize, 0.01f)
  }

  @Test
  fun glyphRowPopulatesEqualCellsWithGapAndClicks() {
    val row = GlyphRow(context)
    val clicks = ArrayList<Int>()
    row.populate(
        listOf(GlyphRow.Item("1", "Video 1"), GlyphRow.Item("2", "Video 2")),
        targetVisualHeightPx = 18f,
        cellSizePx = 72,
        gapPx = 12,
        onClick = { clicks.add(it) },
    )

    assertEquals(LinearLayout.HORIZONTAL, row.orientation)
    assertEquals(2, row.childCount)
    val first = row.getChildAt(0) as GlyphButton
    val second = row.getChildAt(1) as GlyphButton
    assertEquals("Video 1", first.contentDescription)
    assertEquals("Video 2", second.contentDescription)
    assertEquals(72, first.layoutParams.width)
    assertEquals(72, first.layoutParams.height)
    // Gap only between cells (index > 0).
    assertEquals(0, (first.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
    assertEquals(12, (second.layoutParams as ViewGroup.MarginLayoutParams).marginStart)

    (row.getChildAt(1) as GlyphButton).performClick()
    assertEquals(listOf(1), clicks)
  }

  @Test
  fun glyphRowRepopulateReplacesCells() {
    val row = GlyphRow(context)
    row.populate(listOf(GlyphRow.Item("1", "a")), 18f, 72, 12) {}
    row.populate(listOf(GlyphRow.Item("1", "a"), GlyphRow.Item("2", "b")), 18f, 72, 12) {}
    assertEquals(2, row.childCount)
  }

  @Test
  fun glyphRowSingleItemHasNoGap() {
    val row = GlyphRow(context)
    row.populate(listOf(GlyphRow.Item("1", "only")), 18f, 72, 12) {}
    val only = row.getChildAt(0)
    assertEquals(0, (only.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
  }

  @Test
  fun glyphRowCellCapsTextSizeWhenCellIsSmall() {
    // A GlyphRow cell (fixed square set BEFORE renderGlyph) must hit the same fit-cap as any
    // other fixed-height glyph. Regression guard: populate used to render into a height-less
    // cell, so the cap never fired and a large-font-scale row glyph could size past its cell and
    // clip (the video-chip "invisible glyph" report).
    val row = GlyphRow(context)
    row.populate(listOf(GlyphRow.Item("▲", "triangle")), 100f, 48, 0) {}
    val cell = row.getChildAt(0) as GlyphButton
    val metric = GlyphMetrics.metricFor("▲")
    assertNotNull(metric)
    assertTrue(
        "row cell text must be capped below the uncapped equalized size",
        cell.textSize < 100f / metric!!.visualHeightEm,
    )
    val contentHeight =
        cell.paddingTop + cell.paddingBottom + cell.textSize * GlyphMetrics.LINE_BOX_EM
    assertTrue("glyph content must fit the cell", contentHeight <= 48f + 1f)
  }

  @Test
  fun glyphRowCellsShareOneTopLine() {
    // Regression guard (hit 2026-09-05): a horizontal LinearLayout baseline-aligns its equal-height
    // cells, so cells whose glyphs have different baselines (different equalized text sizes /
    // centering padding) were shifted vertically and their full-cell ring backgrounds ended on
    // different top lines — the chips "circles not in a row" report.
    val row = GlyphRow(context)
    row.populate(
        listOf(GlyphRow.Item("1", "a"), GlyphRow.Item("2", "b"), GlyphRow.Item("▲", "c")),
        targetVisualHeightPx = 20f,
        cellSizePx = 120,
        gapPx = 12,
        recenter = false,
    ) {}
    assertFalse("a glyph row must not baseline-align its cells", row.isBaselineAligned)

    val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    row.measure(spec, spec)
    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    val tops = (0 until row.childCount).map { row.getChildAt(it).top }
    assertTrue(
        "equal fixed cells must share one top line so the rings form a row (got tops=$tops)",
        tops.all { it == tops.first() },
    )
  }

  @Test
  fun recenterOffLeavesPlainCenteredTextWithoutPadding() {
    // OFF ("Smart glyph alignment" off, baseline mode): no ink correction — default gravity
    // centering only, so every cell keeps zero padding even after an earlier render.
    val view = GlyphTextView(context)
    view.layoutParams = ViewGroup.MarginLayoutParams(100, 100)
    view.renderGlyph("1", 60f, recenter = false)
    assertEquals(0, view.paddingTop)
    assertEquals(0, view.paddingBottom)
    assertEquals(0, view.paddingStart)
    assertEquals(0, view.paddingEnd)
  }

  @Test
  fun recenterOnPadsOnlyTheNeededSideWithTheMeasuredOffset() {
    // The smart (ON) mode must aim the runtime ink box at the view center via asymmetric padding:
    // exactly one vertical side gets the paint-measured offset and the other stays 0 (never a
    // translation — a translation would move the cell's ring too). The expected magnitude is the
    // paint's own measurement, so the assertions are conditional on that measurement, not a copy of
    // the production math.
    val view = GlyphTextView(context)
    view.layoutParams = ViewGroup.MarginLayoutParams(100, 100)
    view.renderGlyph("1", 60f, recenter = true)
    val paint = view.paint
    val bounds = android.graphics.Rect()
    paint.getTextBounds("1", 0, 1, bounds)
    val fm = paint.fontMetrics
    val lineCenter = (fm.ascent + fm.descent) / 2f
    val inkCenter = (bounds.top + bounds.bottom) / 2f
    val offsetY = inkCenter - lineCenter
    val padsOnlyOneSide = view.paddingTop == 0 || view.paddingBottom == 0
    assertTrue("asymmetric padding must keep one side at 0", padsOnlyOneSide)
    if (offsetY > 0.25f) {
      assertTrue("ink below center must pad the bottom side", view.paddingBottom >= 1)
      assertEquals(0, view.paddingTop)
    } else if (offsetY < -0.25f) {
      assertTrue("ink above center must pad the top side", view.paddingTop >= 1)
      assertEquals(0, view.paddingBottom)
    }
  }

  @Test
  fun metricForEmptyGlyphShouldReturnNull() {
    assertNull(GlyphMetrics.metricFor(""))
  }

  @Test
  fun metricForUnknownGlyphShouldReturnNull() {
    assertNull(GlyphMetrics.metricFor("\uD83E\uDD16"))
  }
}
