package com.trikset.gamepad2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.net.URI
import java.util.Locale

/** Non-null-returning read of a String preference (the default is the contract). */
internal fun SharedPreferences.readString(key: String, default: String): String =
    getString(key, default) ?: default

/**
 * Owns [MainActivity]'s preference-change handling: retargets the [sender] on host/port changes,
 * parses the video URL, animates pad opacity, clamps the wheel step and validates the keepalive
 * timeout. Extracted from MainActivity's inline listener so the logic is directly testable without
 * reflection.
 */
class MainActivitySettingsController(
    context: Context,
    private val sender: SenderService,
    private val ui: SettingsUi,
) {
  /**
   * UI-side callbacks implemented by [MainActivity] (views, title, toast, video URL, wheel step).
   */
  interface SettingsUi {
    fun setTargetChip(host: String)

    fun toast(text: String)

    fun animatePadsAlpha(alpha: Float, previousAlpha: Float)

    fun setVideoUrl(url: String?)

    var wheelStep: Int

    var wheelEnabled: Boolean

    fun setKeepScreenOn(enabled: Boolean)

    fun setMagicButtons(count: Int, symbols: List<String>, sizePercent: Int)

    fun setControlsVisible(visible: Boolean)

    fun setShowFps(enabled: Boolean)

    fun setVideoCropToFill(enabled: Boolean)
  }

  private val context = context.applicationContext
  private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
  private var prevAlpha = 0f
  private var registered = false

  // Private so the listener cannot be registered anywhere else (a leak footgun);
  // register()/unregister() are the only entry points and are idempotent.
  private val listener: SharedPreferences.OnSharedPreferenceChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        onPreferenceChanged(prefs, key)
      }

  fun register() {
    if (registered) {
      return
    }
    registered = true
    onPreferenceChanged(preferences)
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  fun unregister() {
    if (!registered) {
      return
    }
    registered = false
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
  }

  /**
   * Re-applies every runtime setting from [sharedPreferences]. [changedKey] is the preference that
   * actually changed (null on the initial [register] sweep): the custom message is sent to the
   * robot ONLY when its own key changed — `custom <message>` is an edge (the robot's user program
   * reacts to each delivery), so unrelated setting changes or a register sweep must not re-send it.
   */
  fun onPreferenceChanged(sharedPreferences: SharedPreferences, changedKey: String? = null) {
    val addr =
        sharedPreferences.readString(
            SettingsFragment.SK_HOST_ADDRESS,
            SettingsFragment.DEFAULT_HOST_ADDRESS,
        )
    var portNumber = DEFAULT_PORT
    val portStr =
        sharedPreferences.readString(
            SettingsFragment.SK_HOST_PORT,
            SettingsFragment.DEFAULT_HOST_PORT,
        )
    try {
      portNumber = portStr.toInt()
    } catch (e: NumberFormatException) {
      ui.toast(context.getString(R.string.port_number_incorrect, portStr))
    }
    sender.setTarget(addr, portNumber)

    // Command transport: the robot screen's Network category selects TCP or UDP (global setting;
    // per-preset transport is deferred until the robot supports UDP). A change disconnects and the
    // next command reconnects over the new transport.
    val transportKey =
        sharedPreferences.readString(SettingsFragment.SK_TRANSPORT, TRANSPORT_DEFAULT)
    sender.transportMode =
        if (transportKey.equals(TRANSPORT_UDP, ignoreCase = true)) {
          TransportMode.UDP
        } else {
          TransportMode.TCP
        }

    val defAlpha = SettingsFragment.DEFAULT_PADS_ALPHA
    // SeekBarPreference stores Int; legacy String values are still honored.
    val padsAlpha =
        SettingsFragment.readSeekBarValue(
            sharedPreferences,
            SettingsFragment.SK_SHOW_PADS,
            defAlpha,
        )
    val alpha = Math.max(0, Math.min(ALPHA_MAX, padsAlpha)) / ALPHA_MAX.toFloat()
    ui.animatePadsAlpha(alpha, prevAlpha)
    prevAlpha = alpha

    // Empty host = video-only device: the URI default cannot be derived from the host, and the
    // malformed "http://:8080/..." would toast "Illegal video stream URL" on every register.
    // An empty effective URI -> setVideoUrl(null) -> the placeholder prompts the user to configure
    // one. The shared helper mirrors the settings row exactly (both show/use the effective value,
    // so an unset field never reads as "no stream" while the app streams the host-derived default).
    val videoStreamURI = SettingsFragment.effectiveVideoUri(sharedPreferences)

    // The top-left chip shows the robot target: the host when set; else the video stream's host
    // when a stream is configured (video-only mode); else a filler so the chip stays readable
    // instead of empty (DESIGN.md "Defaults are as useful as possible").
    val videoHost = runCatching { URI(videoStreamURI).host }.getOrNull()
    ui.setTargetChip(addr.ifBlank { videoHost?.takeIf { it.isNotBlank() } ?: TARGET_CHIP_EMPTY })

    // The URI flows as an opaque string: validation happens per video player at open time (MJPEG
    // parses the http/https URL, MediaPlayer accepts rtsp:// directly), so an rtsp:// stream never
    // trips URL-only parsing. An empty effective URI = video disabled (DESIGN.md "Empty-value
    // semantics").
    ui.setVideoUrl(videoStreamURI.ifEmpty { null })

    val wheelStep =
        SettingsFragment.readSeekBarValue(
            sharedPreferences,
            SettingsFragment.SK_WHEEL_STEP,
            ui.wheelStep,
        )
    ui.wheelStep = Math.max(WHEEL_STEP_MIN, Math.min(WHEEL_STEP_MAX, wheelStep))

    val wheelEnabled = sharedPreferences.getBoolean(SettingsFragment.SK_WHEEL_ENABLED, false)
    ui.wheelEnabled = wheelEnabled

    val keepScreenOn = sharedPreferences.getBoolean(SettingsFragment.SK_KEEP_SCREEN_ON, true)
    ui.setKeepScreenOn(keepScreenOn)

    // Magic buttons: count (0 hides the row; capped at the maximum) + a display glyph per button
    // (defaults ▲ ■ ● ✕ ◆); the protocol command stays numeric `btn N down`.
    val magicCount = readMagicButtonCount(sharedPreferences)
    val magicSize =
        SettingsFragment.readSeekBarValue(
            sharedPreferences,
            SettingsFragment.SK_MAGIC_BUTTON_SIZE,
            SettingsFragment.DEFAULT_MAGIC_BUTTON_SIZE,
        )
    ui.setMagicButtons(
        magicCount,
        MagicSymbolsStore(sharedPreferences).readAll(),
        magicSize.coerceIn(
            SettingsFragment.MIN_MAGIC_BUTTON_SIZE,
            SettingsFragment.MAX_MAGIC_BUTTON_SIZE,
        ),
    )

    val hideControls = sharedPreferences.getBoolean(SettingsFragment.SK_HIDE_CONTROLS, false)
    // Empty host = video-only device: no pads/buttons to tap regardless of the toggle.
    ui.setControlsVisible(!hideControls && addr.isNotBlank())

    val showFps = sharedPreferences.getBoolean(SettingsFragment.SK_SHOW_FPS, false)
    ui.setShowFps(showFps)

    val cropToFill = sharedPreferences.getBoolean(SettingsFragment.SK_VIDEO_CROP, false)
    ui.setVideoCropToFill(cropToFill)

    try {
      val timeout =
          sharedPreferences
              .readString(
                  SettingsFragment.SK_KEEPALIVE,
                  SenderService.DEFAULT_KEEPALIVE.toString(),
              )
              .toInt()
      if (timeout < SenderService.MINIMAL_KEEPALIVE) {
        ui.toast(
            String.format(
                Locale.ROOT,
                context.getString(R.string.keepalive_must_be_not_less),
                SenderService.MINIMAL_KEEPALIVE,
            )
        )
        sharedPreferences.edit {
          putString(SettingsFragment.SK_KEEPALIVE, sender.keepaliveTimeout.toString())
        }
      } else {
        sender.keepaliveTimeout = timeout
      }
    } catch (e: NumberFormatException) {
      ui.toast(context.getString(R.string.keepalive_must_be_positive_decimal))
      sharedPreferences.edit {
        putString(SettingsFragment.SK_KEEPALIVE, sender.keepaliveTimeout.toString())
      }
    }

    val customMessage = sharedPreferences.getString(SettingsFragment.SK_CUSTOM_MESSAGE, null)
    if (changedKey == SettingsFragment.SK_CUSTOM_MESSAGE && customMessage != null) {
      sender.send("custom $customMessage")
    }
  }

  internal companion object {
    const val TAG = "SettingsController"
    const val DEFAULT_PORT = 4444
    const val TRANSPORT_DEFAULT = "tcp"
    const val TRANSPORT_UDP = "udp"
    const val ALPHA_MAX = 255
    const val WHEEL_STEP_MIN = 1
    const val WHEEL_STEP_MAX = 100
    // Shown in the top-left IP chip when neither a host nor a video stream is configured
    // (the chip stays readable instead of empty).
    const val TARGET_CHIP_EMPTY = "---.---.---.---"

    /** Reads the configured magic-button count (0..MAX), honoring both Int and String storage. */
    fun readMagicButtonCount(prefs: SharedPreferences): Int {
      val parsed =
          when (val value = prefs.all[SettingsFragment.SK_MAGIC_BUTTON_COUNT]) {
            is Int -> value
            is String -> value.toIntOrNull()
            else -> null
          }
      return (parsed ?: SettingsFragment.DEFAULT_MAGIC_BUTTON_COUNT).coerceIn(
          0,
          SettingsFragment.MAX_MAGIC_BUTTONS,
      )
    }
  }
}
