package com.trikset.gamepad2

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.trikset.gamepad2.diagnostics.CrashLogStore
import com.trikset.gamepad2.diagnostics.CrashReportDialog
import com.trikset.gamepad2.mjpeg.ScaleMode
import com.trikset.gamepad2.video.VideoPlayer
import com.trikset.gamepad2.video.VideoPlayerFactory
import kotlinx.coroutines.launch

/**
 * The gamepad: two touch pads, five magic buttons, a sensor-driven wheel and the MJPEG video
 * stream, all sending commands through [SenderService].
 */
class MainActivity :
    AppCompatActivity(), SensorEventListener, MainActivitySettingsController.SettingsUi {

  private var sensorManager: SensorManager? = null
  private var angle = 0 // -100% .. +100%
  override var wheelEnabled: Boolean = false
  override var wheelStep: Int = WHEEL_STEP_DEFAULT
  private var video: VideoPlayer? = null
  private var videoUrl: String? = null
  var settingsController: MainActivitySettingsController? = null
    private set

  private var videoRetryController: VideoRetryController? = null
  // Base pad/button opacity from the "Arrows transparency" setting (1f until the first apply);
  // applyHudTone caps it at the connection-state dim factor via minOf (an upper bound, never a
  // multiplier) so the two never fight.
  private var padsAlphaBase = 1f
  private val senderViewModel: SenderViewModel by viewModels()
  private val wheelController = WheelController()
  private val magicButtons = MagicButtonPanel(this) { senderService.send(it) }
  // Lazy: `window` is only assigned during Activity.attach(), which runs after
  // construction — a field initializer touching it would NPE/throw in Robolectric.
  private val systemUiController: SystemUiController by lazy {
    SystemUiController(
        window,
        mainViewProvider = { findViewById(R.id.main) },
        actionBarProvider = { supportActionBar },
        hideDelayMs = HIDE_DELAY_MS,
    )
  }
  private val connectionFeedback: ConnectionFeedback by lazy {
    ConnectionFeedback(
        context = this,
        settingsButtonProvider = { findViewById(R.id.btnSettings) },
        rootViewProvider = { findViewById(R.id.main) },
        statusTextProvider = { findViewById(R.id.connectionStatus) },
        targetProvider = {
          val sender = senderService
          // Blank host (video-only mode) = no control target: the pill hides entirely (DESIGN.md
          // "Connection & video state UX"). A null target also stops the "tap to connect"
          // affordance (connect() no-ops on a blank host).
          sender.hostAddr?.takeIf { it.isNotBlank() }?.let { host -> "$host:${sender.hostPort}" }
        },
        connectAction = { senderService.connect() },
    )
  }
  private val connectionIndicator = ConnectionIndicator()
  // Robot-target chip controller: owns the host text, both status glyphs and the chip's dynamic
  // contentDescription (extracted so MainActivity stays under the detekt TooManyFunctions gate).
  private val robotChip: RobotChipController by lazy {
    RobotChipController(
        context = this,
        chipProvider = { findViewById(R.id.targetChip) },
        chipTextProvider = { findViewById(R.id.targetChipText) },
        controlAccentProvider = {
          connectionIndicator.accentColorResource(senderViewModel.connectionState.value)
        },
    )
  }
  private val hardwareGamepadController: HardwareGamepadController by lazy {
    HardwareGamepadController(
        send = { senderService.send(it) },
        settings = {
          val prefs = PreferenceManager.getDefaultSharedPreferences(this)
          HardwareGamepadController.Settings(
              swapSticks = prefs.getBoolean(SettingsFragment.SK_GAMEPAD_SWAP, false),
              magicButtonCount = MainActivitySettingsController.readMagicButtonCount(prefs),
          )
        },
    )
  }
  private val videoStreamErrorNotifier = VideoStreamErrorNotifier()
  // Disconnect-alert haptics (two strong pulses, 200 ms between starts); scheduled on the main
  // thread so the pulses read as a pattern (see Haptics.rejectSequence).
  private val rejectHaptic: RejectHaptic by lazy {
    RejectHaptic(viewProvider = { findViewById(R.id.main) })
  }
  // Previous control-connection state, for edge-triggered haptics (medium click on connect, alert
  // on unexpected loss) — fires on the transition, not on every re-emission.
  private var previousConnectionState: ConnectionState? = null

  private fun createPad(id: Int, strId: String) {
    val pad = requireNotNull(findViewById<SquareTouchPadLayout>(id))
    pad.padName = "pad $strId"
    pad.sender = senderService
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // no-op, kept for the SensorEventListener contract
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    // Edge-to-edge (required on API 35+; enforced for targetSdk 36): draw
    // behind the system bars instead of using the removed FLAG_FULLSCREEN.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // Full-bleed past the display cutout: on API 28-34 the DEFAULT mode letterboxes
    // the whole window when the cutout sits on the landscape short edge (a rotated
    // camera hole — the HONOR ALT-LX1 dead-band bug), shifting the video and every
    // control right and leaving a black band. ALWAYS opts back into drawing behind
    // the cutout; the video and pads run full-bleed, the chrome keeps its own small
    // edge margins (it ignores the cutout).
    // API 35+ enforces this for targetSdk 35+ apps; API 27- has no cutouts.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.attributes =
          window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
          }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes =
          window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
          }
    }
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // The edge-pinned chrome (chip, gear, magic buttons) intentionally ignores the window insets:
    // it sits flush to the screen corners/edges with a small margin. On typical hardware the
    // camera is not in the landscape short edge and the bottom gesture-nav overlap is an accepted
    // trade-off (see DECISIONS.md "Display-cutout full-bleed window"). Only the video and pads
    // need cutout clearance, and those are full-bleed siblings below this container.
    systemUiController.setVisibility(false)
    connectionFeedback.attach()
    // No action bar on the gamepad HUD: it was the old green IP bar. The IP now lives in the
    // tappable top-left chip; the gear opens Settings directly (see below).
    supportActionBar?.hide()

    sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    video =
        VideoPlayerFactory.create(
            videoUrl,
            requireNotNull(findViewById(R.id.video)),
            requireNotNull(findViewById(R.id.videoTexture)),
        )

    senderService.setShowTextCallback { message ->
      // The gear border is the persistent status; surface only connection *errors* as feedback,
      // using a Snackbar (Material) instead of a transient Toast.
      if (message.endsWith(ERROR_SUFFIX)) {
        runOnUiThread { connectionFeedback.error(message) }
      }
    }
    val btnSettings = requireNotNull(findViewById<Button>(R.id.btnSettings))
    btnSettings.isHapticFeedbackEnabled = true
    btnSettings.setOnClickListener {
      btnSettings.haptic(Haptics.Level.HEAVY)
      startActivity(Intent(this, SettingsActivity::class.java))
    }
    // Offset the gear button and target chip past the display cutout (punch-hole camera on the
    // left edge in landscape). safeInsetLeft is the pixel width of the camera notch.
    // Only increases the left margin — never reduces it below the XML layout default.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val mainView = findViewById<View>(R.id.main)
      mainView.setOnApplyWindowInsetsListener { _, insets ->
        val cutoutPx = insets.displayCutout?.safeInsetLeft ?: 0
        val halfGlyph = resources.getDimensionPixelSize(R.dimen.hud_half_glyph)
        val requiredMargin = cutoutPx + halfGlyph
        val gearLp = btnSettings.layoutParams as ViewGroup.MarginLayoutParams
        val newGearMargin = maxOf(gearLp.marginStart, requiredMargin)
        if (gearLp.marginStart != newGearMargin) {
          gearLp.marginStart = newGearMargin
          btnSettings.layoutParams = gearLp
        }
        findViewById<View>(R.id.targetChip)?.let { chip ->
          val chipLp = chip.layoutParams as ViewGroup.MarginLayoutParams
          val newChipMargin = maxOf(chipLp.marginStart, requiredMargin)
          if (chipLp.marginStart != newChipMargin) {
            chipLp.marginStart = newChipMargin
            chip.layoutParams = chipLp
          }
        }
        insets
      }
      mainView.requestApplyInsets()
    }

    val targetChip = requireNotNull(findViewById<View>(R.id.targetChip))
    // The IP chip opens the robot/target settings (host, port, video, presets).
    targetChip.setOnClickListener {
      // The chip is a button too — it vibrates like the rest of the HUD controls.
      targetChip.haptic(Haptics.Level.HEAVY)
      startActivity(Intent(this, RobotSettingsActivity::class.java))
    }

    // Child order in activity_main.xml IS the z-order: the pads layer (controlsOverlay) is
    // declared before the visuals, so the chip/gear/buttons draw and receive touches above the
    // pads with no runtime bringToFront.
    createPad(R.id.leftPad, "1")
    createPad(R.id.rightPad, "2")

    settingsController = MainActivitySettingsController(this, senderService, this)
    settingsController?.register()
    // Bounded video-stream retry, gated on a configured URL + a not-playing view only — the control
    // connection is deliberately NOT part of the gate (a dead control must never freeze a dead
    // stream; S2, see DECISIONS.md "Scenario-driven video retry — control gate removed (Option
    // B)").
    // Reloads only while the view is not playing, so a healthy stream is never disturbed; a failed
    // open / silent stall / foldable surface recreation all leave the view not-playing and are
    // recovered by the 5 s tick or by the control-Connected edge (see VideoRetryController).
    videoRetryController =
        VideoRetryController(
            shouldReload = { shouldReloadVideo() },
            reload = { restartVideoStream() },
        )

    // Observe the TCP connection state for the activity's lifetime; repeatOnLifecycle
    // (not the deprecated launchWhenX) stops the collection on STOP and restarts it
    // fresh on each START, so no stale emissions are collected across pauses.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        senderViewModel.connectionState.collect { state ->
          val previous = previousConnectionState
          previousConnectionState = state
          connectionFeedback.update(state)
          applyHudTone(state)
          if (
              state is ConnectionState.Disconnected &&
                  state.reason.isNotEmpty() &&
                  state.reason != ConnectionState.PAUSE_DISCONNECT_REASON
          ) {
            if (previous is ConnectionState.Connected) {
              // Edge: just lost control while driving — the alert buzz (two strong pulses)
              // must be felt even when the eyes are on the robot.
              rejectHaptic.play()
            }
            connectionFeedback.error(getString(R.string.disconnected_notice, state.reason))
          }
          if (state is ConnectionState.Connected) {
            if (previous !is ConnectionState.Connected) {
              // Edge: control acquired (initial connect or reconnect) — a single medium click.
              findViewById<View>(R.id.main)?.haptic(Haptics.Level.CLICK)
            }
            // Edge trigger: robot reachable again -> reload the video right away if it is dead.
            videoRetryController?.onControlConnected()
          }
        }
      }
    }

    // Surface a captured crash exactly once (share / copy / dismiss); see
    // CrashReportDialog. Posted after the first frame: showing an AlertDialog
    // during onCreate on an edge-to-edge fullscreen activity can be dropped
    // before the window attaches, so the dialog is deferred until the gamepad
    // is visible.
    window.decorView.post {
      CrashReportDialog(this, CrashLogStore(this)) { senderViewModel.connectionState.value }
          .showIfNeeded()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean =
      // No action-bar menu on the gamepad: the gear and the IP chip open Settings.
      super.onOptionsItemSelected(item)

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // A hardware gamepad maps to pad/button commands; unmapped keys fall through.
    val handled =
        when (event.action) {
          KeyEvent.ACTION_DOWN ->
              hardwareGamepadController.onKeyDown(event.keyCode, event.repeatCount)
          KeyEvent.ACTION_UP -> hardwareGamepadController.onKeyUp(event.keyCode)
          else -> false
        }
    return handled || super.dispatchKeyEvent(event)
  }

  override fun onGenericMotionEvent(event: MotionEvent): Boolean =
      hardwareGamepadController.onMotionEvent(event) || super.onGenericMotionEvent(event)

  override fun onPause() {
    sensorManager?.unregisterListener(this)
    senderService.disconnect(ConnectionState.PAUSE_DISCONNECT_REASON)
    videoRetryController?.onPause()
    setVideoLoading(false)
    val video = video
    if (video != null) {
      video.stop()
      video.setOnStreamErrorListener(null)
      video.setOnFirstFrameListener(null)
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    videoRetryController?.onResume()
    val video = video
    if (video != null) {
      // Reconnect-on-error: the render thread reports a dead stream and we
      // drop the HTTP connection and restart it (R12; see DECISIONS.md
      // "MJPEG: reconnect-on-error").
      video.setOnStreamErrorListener {
        runOnUiThread {
          robotChip.setVideoStatus(VideoStatus.UNAVAILABLE)
          videoRetryController?.onStreamError()
        }
      }
      // Hide the loading indicator once the first frame of this playback cycle renders; the chip
      // eye glyph flips to streaming at the same moment.
      video.setOnFirstFrameListener {
        runOnUiThread {
          setVideoLoading(false)
          robotChip.setVideoStatus(VideoStatus.PLAYING)
        }
      }
      restartVideoStream()
    }
    val sensorManager = sensorManager
    if (sensorManager != null) {
      sensorManager.registerListener(
          this,
          sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
          SensorManager.SENSOR_DELAY_NORMAL,
      )
    }
  }

  /**
   * Retry gate for the bounded video-stream reload: a configured URL and a not-playing view. The
   * control connection state is deliberately NOT part of the gate — a dead control must never
   * freeze a dead stream (S2; see DECISIONS.md "Scenario-driven video retry — control gate removed
   * (Option B)").
   */
  private fun shouldReloadVideo(): Boolean = videoUrl != null && video?.isPlaying == false

  private fun restartVideoStream() {
    val currentUrl = videoUrl
    val player = video
    if (player == null || currentUrl == null) {
      setVideoLoading(false)
      return
    }
    val wasPlaying = player.isPlaying
    setVideoLoading(true, reconnecting = wasPlaying)
    player.onPlayResult = { ok ->
      if (ok) {
        videoRetryController?.onLoadSuccess()
      } else {
        videoRetryController?.onLoadFailed()
        if (videoStreamErrorNotifier.shouldNotify()) {
          connectionFeedback.error(getString(R.string.video_stream_unavailable))
        }
      }
    }
    player.play(currentUrl)
  }

  private fun setVideoLoading(visible: Boolean, reconnecting: Boolean = false) {
    findViewById<android.widget.ProgressBar>(R.id.videoLoading)?.visibility =
        if (visible) View.VISIBLE else View.GONE
    // The reconnect badge is a companion of the loading indicator: it never shows on its own and
    // hides whenever the spinner hides.
    findViewById<android.widget.TextView>(R.id.videoReconnecting)?.visibility =
        if (visible && reconnecting) View.VISIBLE else View.GONE
    // Keep the chip eye glyph in sync with the spinner: a load in flight is LOADING, a reload of
    // a stream that WAS playing is RECONNECTING. Hiding the spinner never overrides a status
    // (the first-frame listener flips to PLAYING; an error listener flips to UNAVAILABLE).
    if (visible) {
      robotChip.setVideoStatus(if (reconnecting) VideoStatus.RECONNECTING else VideoStatus.LOADING)
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
      processSensor(event.values)
    }
  }

  private fun processSensor(values: FloatArray) {
    val angle =
        wheelController.nextAngle(values[0], values[1], angle, wheelStep, wheelEnabled) ?: return
    this.angle = angle
    senderService.send("wheel $angle")
  }

  override fun setTargetChip(host: String) {
    robotChip.setHost(host)
  }

  override fun toast(text: String) {
    runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
  }

  override fun animatePadsAlpha(alpha: Float, previousAlpha: Float) {
    padsAlphaBase = alpha
    // Single alpha authority: applyHudTone recomputes the final alpha from the connection state,
    // so no competing AlphaAnimation (the old fillAfter animation multiplied with the direct
    // .alpha set below and washed the pads out to ~15% opacity).
    applyHudTone(senderViewModel.connectionState.value)
  }

  override fun setVideoUrl(url: String?) {
    videoUrl = url
    // Replace the running player: stop + release the previous one before building the new one. A
    // player owns a non-daemon executor thread and (while streaming) a live connection, and
    // release() is the only thing that returns them — without it, every replacement leaked the old
    // player (the settings controller re-applies the video URI on every settings change, so a
    // single settings session could leak several players).
    val previous = video
    video = null
    if (previous != null) {
      previous.stop()
      previous.setOnStreamErrorListener(null)
      previous.setOnFirstFrameListener(null)
      previous.release()
    }
    findViewById<android.widget.TextView>(R.id.videoPlaceholder)?.visibility =
        if (url == null) View.VISIBLE else View.GONE
    robotChip.setVideoStatus(if (url == null) VideoStatus.DISABLED else VideoStatus.LOADING)
    video =
        VideoPlayerFactory.create(
            url,
            requireNotNull(findViewById(R.id.video)),
            requireNotNull(findViewById(R.id.videoTexture)),
        )
  }

  override fun setKeepScreenOn(enabled: Boolean) {
    findViewById<View>(R.id.main)?.keepScreenOn = enabled
  }

  override fun setMagicButtons(count: Int, symbols: List<String>, sizePercent: Int) {
    val mainView = findViewById<View>(R.id.main)
    val buttonsView = findViewById<ViewGroup>(R.id.buttons) ?: return
    val recenter =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(
                SettingsFragment.SK_RECENTER_GLYPHS,
                SettingsFragment.DEFAULT_RECENTER_GLYPHS,
            )
    magicButtons.populate(buttonsView, count, symbols, recenter, sizePercent)

    buttonsView.doOnLayout { applyButtonMargin(mainView, buttonsView) }
  }

  private fun applyButtonMargin(mainView: View, buttonsView: View) {
    val controlsOverlay = findViewById<View>(R.id.controlsOverlay) ?: return
    val chip = findViewById<View>(R.id.targetChip) ?: return
    val buttonTop = buttonsView.top
    val chipBottom = chip.bottom
    // Center pads vertically between the chip bottom and button top, producing
    // equal gap on both sides. Works on any screen size.
    val controlsOverlayH = chipBottom + buttonTop
    val bottomInset = mainView.height - controlsOverlayH
    if (bottomInset > 0) {
      val lp = controlsOverlay.layoutParams as ViewGroup.MarginLayoutParams
      if (lp.bottomMargin != bottomInset) {
        lp.bottomMargin = bottomInset
        controlsOverlay.layoutParams = lp
      }
    }
  }

  override fun setControlsVisible(visible: Boolean) {
    // GONE removes the pads/buttons from the layout entirely (no hit-testing).
    val visibility = if (visible) View.VISIBLE else View.GONE
    findViewById<View>(R.id.controlsOverlay)?.visibility = visibility
    findViewById<View>(R.id.buttons)?.visibility = visibility
  }

  /**
   * Applies the connection-state accent (green/amber/sepia/red — see [ConnectionIndicator]) to the
   * pads, magic buttons and pill, and dims the controls while not Connected so the whole HUD reads
   * one state. Only the tone/alpha change; the pad touch math and button logic are untouched.
   */
  private fun applyHudTone(state: ConnectionState) {
    val accent = connectionIndicator.accentColorResource(state)
    findViewById<SquareTouchPadLayout>(R.id.leftPad)?.setAccent(accent)
    findViewById<SquareTouchPadLayout>(R.id.rightPad)?.setAccent(accent)
    magicButtons.setAccent(accent)
    // The robot chip's CONTROL glyph reflects the robot control status (same accent as the
    // pads/gear); the host text stays neutral white and the VIDEO glyph is painted separately by
    // robotChip.setVideoStatus.
    robotChip.paintControlAccent(accent)
    // Dim the controls to ~40% until connected (a disconnected gamepad is a standby surface, not
    // a dead one — the tone change + pill text carry the state). The dim is "at most 40%": it
    // never makes the controls more transparent than the user's "Arrows transparency" base, so a
    // default installation (pads already at ~39%) keeps its chrome visible while a fully-opaque
    // setup visibly dims.
    val targetAlpha =
        if (state is ConnectionState.Connected) {
          padsAlphaBase
        } else {
          minOf(padsAlphaBase, CONTROLS_DIM_ALPHA)
        }
    findViewById<View>(R.id.controlsOverlay)?.alpha = targetAlpha
    findViewById<View>(R.id.buttons)?.alpha = targetAlpha
  }

  override fun setShowFps(enabled: Boolean) {
    video?.showFps = enabled
  }

  override fun setVideoCropToFill(enabled: Boolean) {
    video?.scaleMode = if (enabled) ScaleMode.CROP else ScaleMode.FIT
  }

  val senderService: SenderService
    get() = senderViewModel.sender

  // A method (not a property setter) because it accepts null as a safe no-op: injecting a null
  // sender must keep the existing one (asserted by setSenderServiceWithNullShouldBeSafe).
  fun setSenderService(sender: SenderService?) {
    if (sender != null) {
      senderViewModel.sender = sender
    }
  }

  override fun onDestroy() {
    sensorManager?.unregisterListener(this)
    setVideoLoading(false)
    val video = video
    if (video != null) {
      video.stop()
      video.setOnStreamErrorListener(null)
      video.setOnFirstFrameListener(null)
      video.release()
      this.video = null
    }
    systemUiController.detach()
    rejectHaptic.cancel()
    val buttonsView = findViewById<ViewGroup>(R.id.buttons)
    if (buttonsView != null) {
      magicButtons.clearListeners(buttonsView)
    }
    findViewById<Button>(R.id.btnSettings)?.setOnClickListener(null)
    findViewById<SquareTouchPadLayout>(R.id.leftPad)?.sender = null
    findViewById<SquareTouchPadLayout>(R.id.rightPad)?.sender = null
    settingsController?.unregister()
    settingsController = null
    senderService.setShowTextCallback(null)
    super.onDestroy()
  }

  private companion object {
    const val TAG = "MainActivity"
    const val HIDE_DELAY_MS = 3000L
    const val WHEEL_STEP_DEFAULT = 7
    const val ERROR_SUFFIX = " error."
    // Type 1 HUD: controls opacity while not Connected (see applyHudTone).
    const val CONTROLS_DIM_ALPHA = 0.4f
  }
}
