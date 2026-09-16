package com.trikset.gamepad2.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.BuildConfig
import com.trikset.gamepad2.ConnectionState
import com.trikset.gamepad2.RobotPresetStore
import com.trikset.gamepad2.SettingsFragment
import java.util.Locale

/**
 * Builds the one-file diagnostic "data block" users share with developers: app/device/display
 * identity, the live connection state, the full settings snapshot (non-defaults marked) and the
 * recent [AppLog] tail, optionally followed by a crash stack trace. [build] is pure formatting —
 * inputs are passed in, so every section is unit-testable under Robolectric — while [fromAppState]
 * gathers the app-level inputs (default prefs, log tail, latest crash) for entry points that have
 * no live connection state to report.
 */
object DiagnosticsReport {

  /**
   * The default-prefs app snapshot: no live connection state ("not running" line), as the Settings
   * "Report an issue" row produces. Shared by that row and the image share receiver.
   */
  fun fromAppState(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val crash = CrashLogStore(context).latest()
    return build(
        context,
        prefs,
        null,
        AppLog.tail(AppLog.BUFFER_CAPACITY),
        crash?.stackTrace,
    )
  }

  fun build(
      context: Context,
      prefs: SharedPreferences,
      connectionState: ConnectionState?,
      logTail: List<String>,
      crashTrace: String?,
      systemInsets: String? = null,
      hudBounds: String? = null,
  ): String {
    val resources = context.resources
    val metrics = resources.displayMetrics
    val configuration = resources.configuration
    val dpWidth = (metrics.widthPixels / metrics.density).toInt()
    val dpHeight = (metrics.heightPixels / metrics.density).toInt()
    val out = StringBuilder()
    out.appendLine("# TRIK Gamepad diagnostic report")
    out.appendLine()
    out.appendLine("## App")
    out.appendLine("- Version: ${BuildConfig.VERSION_NAME}")
    out.appendLine("- Version code: ${BuildConfig.VERSION_CODE}")
    out.appendLine("- Build type: ${BuildConfig.BUILD_TYPE}")
    out.appendLine()
    out.appendLine("## Device")
    out.appendLine("- Manufacturer: ${Build.MANUFACTURER}")
    out.appendLine("- Model: ${Build.MODEL}")
    out.appendLine("- Product: ${Build.PRODUCT}")
    out.appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    out.appendLine()
    out.appendLine("## Display")
    out.appendLine(
        "- Resolution: ${metrics.widthPixels}x${metrics.heightPixels} px (${dpWidth}x${dpHeight} dp)"
    )
    out.appendLine("- Density: ${metrics.densityDpi} dpi; font scale ${configuration.fontScale}")
    if (systemInsets != null) {
      out.appendLine("- Window insets (top/bottom/left/right): $systemInsets px")
    }
    if (hudBounds != null) {
      out.appendLine("- HUD bounds (px): $hudBounds")
    }
    out.appendLine("- Locale: ${Locale.getDefault()}")
    out.appendLine()
    out.appendLine("## Connection")
    out.appendLine("- ${connectionLine(connectionState)}")
    out.appendLine()
    out.appendLine("## Settings")
    appendSettings(out, prefs)
    out.appendLine()
    out.appendLine("## Recent log")
    out.appendLine("```text")
    if (logTail.isEmpty()) {
      out.appendLine("(no log entries)")
    } else {
      for (line in logTail) {
        out.appendLine(line)
      }
    }
    out.appendLine("```")
    if (crashTrace != null) {
      out.appendLine()
      out.appendLine("## Last crash")
      out.appendLine("```text")
      out.appendLine(crashTrace.trim())
      out.appendLine("```")
    }
    return out.toString()
  }

  private fun connectionLine(state: ConnectionState?): String =
      when (state) {
        null -> "State: not running (open the gamepad to capture the live state)"
        is ConnectionState.Connected -> "State: Connected"
        is ConnectionState.Connecting -> "State: Connecting"
        is ConnectionState.Disconnected -> "State: Disconnected (${state.reason})"
      }

  private fun appendSettings(out: StringBuilder, prefs: SharedPreferences) {
    out.appendLine(
        settingLine(
            prefs,
            "Robot IP address",
            SettingsFragment.SK_HOST_ADDRESS,
            SettingsFragment.DEFAULT_HOST_ADDRESS,
        )
    )
    out.appendLine(
        settingLine(
            prefs,
            "Robot TCP port",
            SettingsFragment.SK_HOST_PORT,
            SettingsFragment.DEFAULT_HOST_PORT,
        )
    )
    out.appendLine(settingLine(prefs, "Video stream URI", SettingsFragment.SK_VIDEO_URI, ""))
    out.appendLine(
        settingLine(prefs, "Keep-alive timeout, ms", SettingsFragment.SK_KEEPALIVE, "5000")
    )
    out.appendLine(settingLine(prefs, "Keep screen on", SettingsFragment.SK_KEEP_SCREEN_ON, "true"))
    out.appendLine(
        settingLine(
            prefs,
            "Arrow transparency",
            SettingsFragment.SK_SHOW_PADS,
            SettingsFragment.DEFAULT_PADS_ALPHA.toString(),
        )
    )
    out.appendLine(
        settingLine(prefs, "Hide pads & buttons", SettingsFragment.SK_HIDE_CONTROLS, "false")
    )
    out.appendLine(settingLine(prefs, "Show FPS", SettingsFragment.SK_SHOW_FPS, "false"))
    out.appendLine(settingLine(prefs, "Wheel enabled", SettingsFragment.SK_WHEEL_ENABLED, "false"))
    out.appendLine(
        settingLine(
            prefs,
            "Wheel sensitivity",
            SettingsFragment.SK_WHEEL_STEP,
            SettingsFragment.DEFAULT_WHEEL_STEP.toString(),
        )
    )
    out.appendLine(settingLine(prefs, "Swap sticks", SettingsFragment.SK_GAMEPAD_SWAP, "false"))
    out.appendLine(
        settingLine(
            prefs,
            "Magic buttons",
            SettingsFragment.SK_MAGIC_BUTTON_COUNT,
            SettingsFragment.DEFAULT_MAGIC_BUTTON_COUNT.toString(),
        )
    )
    for (n in 1..SettingsFragment.MAX_MAGIC_BUTTONS) {
      val stored = prefs.all[SettingsFragment.magicSymbolKey(n)]?.toString()
      if (stored != null) {
        out.appendLine("- Button $n symbol: $stored")
      }
    }
    out.appendLine(
        settingLine(
            prefs,
            "Button size",
            SettingsFragment.SK_MAGIC_BUTTON_SIZE,
            SettingsFragment.DEFAULT_MAGIC_BUTTON_SIZE.toString(),
        )
    )
    out.appendLine(
        settingLine(
            prefs,
            "Smart glyph alignment",
            SettingsFragment.SK_RECENTER_GLYPHS,
            "true",
        )
    )
    out.appendLine(
        settingLine(prefs, "Diagnostics verbosity", SettingsFragment.SK_DIAG_LEVEL, "info")
    )
    out.appendLine(
        settingLine(
            prefs,
            "Share without editing",
            SettingsFragment.SK_SHARE_WITHOUT_EDITING,
            "true",
        )
    )
    out.appendLine(
        settingLine(prefs, "Crop to fill screen", SettingsFragment.SK_VIDEO_CROP, "false")
    )
    val presets = RobotPresetStore(prefs).all().values.map { it.name }.sorted()
    out.appendLine(
        "- Robot presets: " + if (presets.isEmpty()) "none" else presets.joinToString(", ")
    )
  }

  private fun settingLine(
      prefs: SharedPreferences,
      label: String,
      key: String,
      default: String,
  ): String {
    val stored = prefs.all[key]?.toString()
    val value = stored ?: default
    val marker = if (stored == null || stored == default) " (default)" else ""
    return "- $label: $value$marker"
  }
}
