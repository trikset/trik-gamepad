package com.trikset.gamepad2

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.trikset.gamepad2.glyphs.GlyphRow
import kotlin.math.roundToInt

/**
 * A single preference row in Robot settings (Video category) that renders the four video-source
 * preset chips ([VideoSourceChip]) as a glyph-only [GlyphRow]. The row is not itself a tap target;
 * each chip carries its own click listener wired to [onChipSelected]. The tap action (writing the
 * target URI + refreshing the video-URI row) lives in SettingsFragment, which keeps this preference
 * dumb and the behavior testable without a bound list row.
 *
 * Geometry: each chip is a [CHIP_CELL_DP]-square button whose visible chrome is a border-only ring
 * drawn over the ENTIRE cell (no inset halo): the ring IS the tap target. The ring drawable carries
 * no intrinsic padding, so it cannot disturb the glyph's asymmetric ink-centering padding; chrome
 * is still applied before the render (the [GlyphRow.populate] ordering contract). The glyph's
 * VISUAL ink height is [CHIP_GLYPH_RATIO] of the ring diameter (the same ratio the magic buttons
 * use), and the real inter-cell margin ([CHIP_GAP_DP]) is the visible air between neighbouring
 * rings.
 *
 * Live re-render: the glyph alignment follows the global "Smart glyph alignment" toggle, which
 * lives on the App-settings screen (a sibling activity). That screen can sit on top of Robot
 * settings in back stack, so this row registers a change listener while attached and re-populates
 * its bound row when [SettingsFragment.SK_RECENTER_GLYPHS] flips — the settings RecyclerView does
 * not re-bind rows on resume, so without the listener a returned-to Robot screen would keep the old
 * alignment.
 */
class VideoSourceChipsPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

  /** Invoked with the tapped chip; set by SettingsFragment when the screen initializes. */
  var onChipSelected: ((VideoSourceChip) -> Unit)? = null

  /**
   * The row view most recently handed to [onBindViewHolder] (re-populated on a recenter toggle).
   */
  private var boundRow: GlyphRow? = null

  /**
   * Re-renders the bound row when the "Smart glyph alignment" toggle flips while this preference is
   * attached (see the class doc). Registered/unregistered against the fragment screen's view
   * lifetime via [onAttached]/[onDetached], so the listener stays live while Robot settings is
   * merely paused under the App-settings screen in the back stack.
   */
  private val recenterChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsFragment.SK_RECENTER_GLYPHS) {
          boundRow?.let { row -> populateRow(row) }
        }
      }

  init {
    layoutResource = R.layout.pref_video_source_chips
    isSelectable = false
  }

  override fun onAttached() {
    super.onAttached()
    PreferenceManager.getDefaultSharedPreferences(context)
        .registerOnSharedPreferenceChangeListener(recenterChangeListener)
  }

  override fun onDetached() {
    PreferenceManager.getDefaultSharedPreferences(context)
        .unregisterOnSharedPreferenceChangeListener(recenterChangeListener)
    boundRow = null
    super.onDetached()
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    // The chips layout's root can be a wrapper (the preference framework can strip the root's
    // android:id during inflation — observed: itemView.id == 0 at bind), so resolve the row by id
    // with a direct-cast fallback for the row-as-root case.
    val row =
        holder.itemView as? GlyphRow
            ?: holder.itemView.findViewById<GlyphRow>(R.id.videoSourceChipsRow)
            ?: return
    boundRow = row
    populateRow(row)
  }

  /**
   * Populates [row] with the four chips from current preferences. Shared by [onBindViewHolder] and
   * the recenter change listener, so a toggle re-render and an initial bind always agree.
   */
  private fun populateRow(row: GlyphRow) {
    val resources = row.resources
    val density = resources.displayMetrics.density
    val chips = VideoSourceChip.entries
    val recenter =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(
                SettingsFragment.SK_RECENTER_GLYPHS,
                SettingsFragment.DEFAULT_RECENTER_GLYPHS,
            )
    // Visible ring chrome per chip: the border-only ring fills the whole cell (no InsetDrawable),
    // so it has no intrinsic insets for the View to apply as padding and cannot clobber the glyph's
    // ink-centering padding. The glyph color must be set explicitly (not the themed Button
    // default):
    // the chips sit on the DayNight settings list, where an inherited color is never
    // contrast-verified
    // and can vanish on one theme (the tester's "no glyph visible" report). WcagContrastTest covers
    // the chip_glyph day/night pairs.
    val ring = ResourcesCompat.getDrawable(resources, R.drawable.hud_chip_ring, null)
    val glyphColor = ContextCompat.getColor(row.context, R.color.chip_glyph)
    row.populate(
        items = chips.map { GlyphRow.Item(it.glyph, resources.getString(it.descriptionRes)) },
        targetVisualHeightPx = CHIP_GLYPH_RATIO * CHIP_CELL_DP * density,
        cellSizePx = (CHIP_CELL_DP * density).roundToInt(),
        gapPx = (CHIP_GAP_DP * density).roundToInt(),
        recenter = recenter,
        // The ring is set BEFORE renderGlyph (GlyphRow.populate applies chrome before rendering) so
        // the render's asymmetric ink-centering padding lands last; see the class doc.
        chrome = { chip ->
          chip.background = ring
          chip.setTextColor(glyphColor)
        },
        onClick = { index -> onChipSelected?.invoke(chips[index]) },
    )
  }

  companion object {
    /**
     * Glyph VISUAL ink height as a fraction of the ring (= cell) diameter (60%, like the magic
     * buttons).
     */
    private const val CHIP_GLYPH_RATIO = 0.6f
    /** Tappable cell edge AND the visible ring diameter: the border ring fills the cell. */
    private const val CHIP_CELL_DP = 48f
    /** Real margin between neighbouring cells = the visible air between the rings. */
    private const val CHIP_GAP_DP = 16f
  }
}
