package com.trikset.gamepad2

import androidx.preference.PreferenceManager
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Common Robolectric test base: carries the 3-SDK [Config] triple. Robolectric resolves [Config]
 * (and `@LooperMode`) from superclasses (probed 2026-08-09), so subclasses only need
 * `@RunWith(RobolectricTestRunner::class)` plus their own `@GraphicsMode`/`@LooperMode` where they
 * differ. Note [Config.OLDEST_SDK] is the sentinel -4, resolved to the app minSdk (23).
 */
@Config(sdk = [Config.OLDEST_SDK, Config.TARGET_SDK, Config.NEWEST_SDK])
open class RobolectricTestBase {

  /**
   * Bounded poll for [condition] over up to [timeoutMs] (TESTING.md "Why awaits are required": a
   * bare assert right after `runAll()`/`idle()` races the async server/receive thread). Returns
   * `true` as soon as the condition holds. Used for robot→app messages whose arrival is driven by
   * the transport's own receive thread, which the paused executor cannot drain.
   */
  protected fun runBounded(
      timeoutMs: Long = 5_000,
      condition: () -> Boolean,
  ): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (condition()) return true
      Thread.sleep(20)
    }
    return condition()
  }

  /** Walks a dialog's window tree for views matching [predicate] (appcompat dialog internals). */
  protected fun dialogViews(
      dialog: androidx.appcompat.app.AlertDialog,
      predicate: (android.view.View) -> Boolean,
  ): List<android.view.View> {
    val found = ArrayList<android.view.View>()
    fun collect(view: android.view.View) {
      if (predicate(view)) found.add(view)
      if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) collect(view.getChildAt(i))
      }
    }
    collect(dialog.window?.decorView ?: return emptyList())
    return found
  }

  /**
   * Reads a private field via reflection (the "inject a collaborator" pattern used across the
   * activity/controller tests). Deduplicated from the former per-class copies (MainActivityTest,
   * HudThemeTest, SquareTouchPadLayoutTest).
   */
  protected fun field(target: Any, name: String): Any? {
    val f: Field = target.javaClass.getDeclaredField(name)
    f.isAccessible = true
    return f.get(target)
  }

  /** Writes a private field via reflection (see [field]). */
  protected fun setField(target: Any, name: String, value: Any?) {
    val f: Field = target.javaClass.getDeclaredField(name)
    f.isAccessible = true
    f.set(target, value)
  }

  /** Reflects a private method (see [field]); the caller invokes it. */
  protected fun method(target: Any, name: String, vararg params: Class<*>): Method {
    val m = target.javaClass.getDeclaredMethod(name, *params)
    m.isAccessible = true
    return m
  }

  /**
   * Stores [value] under [key] in the default prefs; optionally [controller] is notified (the
   * MainActivity settings tests' "act" step — setting the pref must re-run onPreferenceChanged).
   * Deduplicated from the former per-class copies (MainActivityTest, HudThemeTest).
   */
  protected fun setPref(
      key: String,
      value: String,
      controller: MainActivitySettingsController? = null,
  ) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
    prefs.edit().putString(key, value).commit()
    controller?.onPreferenceChanged(prefs)
  }
}
