package com.trikset.gamepad2

import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast

/** Direct tests for the shared [copyToClipboard] clipboard+toast helper. */
@RunWith(RobolectricTestRunner::class)
class ClipboardUiTest : RobolectricTestBase() {

  @Test
  fun copyToClipboardStoresPrimaryClipUnderLabel() {
    val context = RuntimeEnvironment.getApplication()
    context.copyToClipboard("label", "text")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val primary = clipboard.primaryClip
    assertNotNull(primary)
    assertEquals("text", primary!!.getItemAt(0).text.toString())
  }

  @Test
  fun copyToClipboardShowsCopiedToast() {
    val context = RuntimeEnvironment.getApplication()
    context.copyToClipboard("label", "text")

    assertEquals(
        context.getString(R.string.copied_to_clipboard),
        ShadowToast.getTextOfLatestToast(),
    )
  }
}
