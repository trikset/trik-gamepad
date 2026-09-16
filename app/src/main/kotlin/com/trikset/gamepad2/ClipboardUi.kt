package com.trikset.gamepad2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Copies [text] to the clipboard under [clipLabel] and confirms with the standard "copied to
 * clipboard" toast. Single home for the copy UX shared by the settings rows and the crash dialog,
 * so no caller re-implements the clipboard/toast pair.
 */
fun Context.copyToClipboard(clipLabel: CharSequence, text: CharSequence) {
  val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
  Toast.makeText(applicationContext, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
}
