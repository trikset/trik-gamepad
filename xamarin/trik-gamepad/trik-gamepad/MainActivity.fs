namespace com.trik.gamepad

open System

open Android.App
open Android.Content
open Android.OS
open Android.Runtime
open Android.Views
open Android.Widget
open Android.Hardware
open Android.Util
open Android.Graphics
[<Activity (Label = "Trik Gamepad", MainLauncher = true, Theme = "@android:style/Theme.NoTitleBar.Fullscreen" )>]
type MainActivity () as self =
  
    inherit Activity ()
    let mutable _sensorManager: SensorManager option = None
    let mutable _wheelEnabled = false
    let mutable _angle_ = 0
    let _wheelSensitivity = ref 5
    
    let _videoStream = new MjpegStream()
    let _transmitter = Transmitter.create ()
    let _send = _transmitter.Post << Transmitter.Message.Send
    let _pads = [Resource_Id.leftPad; Resource_Id.rightPad] 
    let toast (msg:string) =  self.RunOnUiThread(fun () -> Toast.MakeText(self, msg, ToastLength.Short).Show())
    let WHEEL_BOOSTER_MULTIPLIER =  1.5 * 200.0 / Math.PI

    let accelPrecision = 0.001f
    let processSensor (current:Collections.Generic.IList<_>) =         
        let x = current.[0]
        let y = current.[1]
        if x > accelPrecision && y > accelPrecision then 
            let angle = int  <|  WHEEL_BOOSTER_MULTIPLIER  * Math.Atan2(float y, float x) 
            let angle =  angle / !_wheelSensitivity * !_wheelSensitivity
            let angle = if angle >  100 then 100 
                        elif angle < -100 then -100
                        else angle
            self.Angle <- angle
    
    let onPreferenceChanged (prefs:ISharedPreferences) =
        let addr = prefs.GetString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
        let portNumber = 4444;
        let portStr = prefs.GetString(SettingsActivity.SK_HOST_PORT, "4444");
        let (s,r) = Int32.TryParse(portStr)
        let p = if  s then r 
                else (toast <| sprintf "Incorrect port number '%s'." portStr ; portNumber)

        _transmitter.Post <| Transmitter.Connect(addr, p) 

        let alphaMax = 0xFF
        let (ok, alpha) = Int32.TryParse <| prefs.GetString(SettingsActivity.SK_SHOW_PADS, "50")
        let alpha = if ok then alpha * alphaMax / 100 else alphaMax / 2 
                    |> min alphaMax |> max 0
                      
        
        _pads |> List.iter ( fun p -> 
                    let pad = self.FindViewById<SquareTouchPadLayout> p 
                    pad.SetAlpha alpha)
             
        let defWheelSensitivity = 5
        let (ok, res) = Int32.TryParse <| prefs.GetString(SettingsActivity.SK_WHEEL_SENSITIVITY, string defWheelSensitivity)
        _wheelSensitivity := if res <= 0 || res > 100 then defWheelSensitivity else res
          
        let videoStreamURI = prefs.GetString(SettingsActivity.SK_VIDEO_URI, "")
                // --no-sout-audio --sout
                // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                // works only with vcodec=mp4v without audio :(

                // http://developer.android.com/reference/android/media/MediaPlayer.html
                // http://developer.android.com/guide/appendix/media-formats.html

        _videoStream.Uri <- videoStreamURI
             

    let onCreate () = 
        self.SetContentView Resource_Layout.activity_main
        self.RequestedOrientation <- PM.ScreenOrientation.Landscape

        _sensorManager <- Some <| downcast self.GetSystemService Context.SensorService

        self.RecreateMagicButtons 5


        let tglWheel = self.FindViewById<ToggleButton> Resource_Id.tglWheel
        tglWheel.CheckedChange.Add(fun x -> _wheelEnabled <- x.IsChecked; _angle_ <- -1000)

        let btnSettings = self.FindViewById<Button> Resource_Id.btnSettings
        btnSettings.Click.Add <| fun _ -> self.StartActivity typeof<SettingsActivity>

        //let mainLayout = self.FindViewById<_> Resource_Id.main
        //let cnt = ref 0

        let surface = self.FindViewById<SurfaceView> Resource_Id.video
        let surfaceHolder = surface.Holder
        surfaceHolder.AddCallback self
        //surfaceHolder.SetType SurfaceType.PushBuffers 
        surfaceHolder.SetSizeFromLayout()

        let paint = new Paint(PaintFlags.AntiAlias, Color = Color.Red, TextSize = 40.0f)
        let cnt = ref 0
        let skipped = ref 0
        let prev = ref DateTime.Now
        let text = ref ""
                    
        let rec render () =
            async {
                
                let! bmp = Async.AwaitEvent _videoStream.FrameReady
                let! bmp = Async.AwaitTask bmp
                incr cnt
                if !cnt % 10 = 0 then 
                    let now = System.DateTime.Now 
                    let elapsed:TimeSpan = now - !prev
                    prev := now
                    text := sprintf "FPS: %.1f Skipped: %d" (10000.0 / (float <| elapsed.TotalMilliseconds)) !skipped

                lock (surfaceHolder) <|  fun () ->
                    let canvas = surfaceHolder.LockCanvas()
                    try 
                        if canvas = null  then incr skipped else
                        let (ch,cw) = float32 canvas.Height, float32 canvas.Width
                        let (ih,iw) = float32 bmp.Height, float32 bmp.Width
                        let scale = Math.Min(ch/ih, cw/iw)
                        let m = canvas.Matrix
                        m.PostScale(scale, scale) |> ignore
                        //let h = scale * ih
                        //let w = scale * iw 
                        //m.PostTranslate((ch - h)/2.0f, (cw - w)/2.0f) |> ignore
                        //canvas.DrawColor Color.Black 
                        
                        if bmp = null then incr skipped else
                            canvas.DrawBitmap(bmp, m, null)

                        canvas.DrawText(!text, cw/2.0f, ch/2.0f, paint)
                
                    finally 
                        if canvas <> null then
                            surfaceHolder.UnlockCanvasAndPost canvas
                return! render ()    
             }
        render ()  |> Async.Start

            
        _videoStream.Completed.Add <| fun () -> (toast "Restarting video.."; _videoStream.StartAsync () |> ignore)
        _videoStream.Error.Add <| fun e -> toast e.Message

        (self.FindViewById<_> Resource_Id.controlsOverlay).BringToFront()

        let vibrator:Vibrator = downcast self.GetSystemService Context.VibratorService

        _pads |> List.iteri  (fun i id ->  
            let send a b =  vibrator.Vibrate 10L; _send <| sprintf "pad %d %O %O" (i+1) a b
            (self.FindViewById<SquareTouchPadLayout> id).PadActivity.Add 
            <| fun ((mea:MotionEventActions, (x,y)) as event) ->
                match mea with
                    MotionEventActions.Down | MotionEventActions.Move -> send x y 
                    | MotionEventActions.Up -> send "up" ""
                    | _ -> ()
            )

    member this.Angle with get() = _angle_ 
                      and  set v = if _angle_ <> v then
                                     _angle_ <- v
                                     _send <| "wheel " + v.ToString()
     
    override this.OnCreate (bundle) =
        base.OnCreate (bundle)
        onCreate()
        let prefs = Android.Preferences.PreferenceManager.GetDefaultSharedPreferences Application.Context
        prefs.RegisterOnSharedPreferenceChangeListener this
        onPreferenceChanged prefs
 
    override this.OnResume() = 
        base.OnResume()
        _sensorManager.Value.RegisterListener(this:>ISensorEventListener, 
                                              _sensorManager.Value.GetDefaultSensor SensorType.All,
                                              SensorDelay.Game) |> ignore 
            
    override this.OnStop() = 
        _sensorManager.Value.UnregisterListener(this)
        base.OnStop()
    
    member this.RecreateMagicButtons(count) = 
        let buttonsView = this.FindViewById<ViewGroup>(Resource_Id.buttons)
        buttonsView.RemoveAllViews()
        for i in 1..count do
            let name = string i
            let btn = new Button(this, Gravity = GravityFlags.Center, Text = name)
            btn.SetBackgroundResource Resource_Drawable.button_shape 
            btn.Click.Add <| fun e -> _send <| "btn " + name + " down" // TBD: "up"                                                                 
            buttonsView.AddView(btn)
        
    interface ISensorEventListener with
        member x.OnAccuracyChanged (sensor, accuracy) = ()
        member x.OnSensorChanged (event) = 
            if _wheelEnabled && event.Sensor.Type = SensorType.Accelerometer then
                processSensor event.Values
    
    interface ISharedPreferencesOnSharedPreferenceChangeListener with
        member this.OnSharedPreferenceChanged (prefs, _) = onPreferenceChanged prefs  
  
    interface IJavaObject with
        member this.Handle = this.Handle
    
    interface ISurfaceHolderCallback with
        member this.SurfaceCreated holder = 
            toast "Starting video "
            _videoStream.StartAsync () |> ignore 
        member this.SurfaceDestroyed holder = 
            toast "Stopping video"
            _videoStream.StopAsync () 
        member this.SurfaceChanged (holder, format, width, height) = ()