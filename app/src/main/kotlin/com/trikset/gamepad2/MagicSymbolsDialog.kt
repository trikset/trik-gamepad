package com.trikset.gamepad2

import android.app.Activity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog

/**
 * Dialog to edit the 5 magic-button glyphs. The fields are pre-filled with the currently resolved
 * symbols ([MagicSymbolsStore.readAll] — defaults ▲ ■ ● ✕ ◆ when unset); "Use default symbols"
 * refills the fields without dismissing; Save writes the whole array via [MagicSymbolsStore.save].
 */
object MagicSymbolsDialog {

  private const val FIELD_PADDING_PX = 48
  private const val FIELD_TOP_PADDING_PX = 8

  fun show(activity: Activity, store: MagicSymbolsStore, onSaved: () -> Unit) {
    val container = LinearLayout(activity)
    container.orientation = LinearLayout.VERTICAL
    container.setPadding(FIELD_PADDING_PX, FIELD_TOP_PADDING_PX, FIELD_PADDING_PX, 0)
    val fields = ArrayList<EditText>(MagicSymbolsStore.MAX_BUTTONS)
    val initial = store.readAll()
    for (n in 1..MagicSymbolsStore.MAX_BUTTONS) {
      val field = EditText(activity)
      field.hint = activity.getString(R.string.magic_symbols_dialog_hint, n)
      field.setText(initial[n - 1])
      field.isSingleLine = true
      container.addView(
          field,
          LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT,
          ),
      )
      fields.add(field)
    }

    val dialog =
        AlertDialog.Builder(activity)
            .setTitle(R.string.magic_symbols_dialog_title)
            .setView(container)
            .setNeutralButton(R.string.magic_symbols_use_defaults, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.magic_symbols_save) { _, _ ->
              store.save(fields.map { it.text.toString().trim() })
              onSaved()
            }
            .create()
    // Refill the fields without dismissing the dialog.
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
        store.defaults().forEachIndexed { i, glyph -> fields[i].setText(glyph) }
      }
    }
    dialog.show()
  }
}
