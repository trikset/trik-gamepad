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
    let _videoStream = new MjpegStream()
    let _transmitter = Transmitter.create ()
    let _send = _transmitter.Post << Transmitter.Message.Send
    let _pads = [Resource_Id.leftPad; Resource_Id.rightPad] 

    let toast (msg:string) =  self.RunOnUiThread(fun () -> Toast.MakeText(self, msg, ToastLength.Long).Show())
    let WHEEL_BOOSTER_MULTIPLIER =  1.5 * 200.0 / Math.PI
    let processSensor (current:Collections.Generic.IList<_>) =         
        let x = current.[0]
        let y = current.[1]
        if x > 1e-4f && y > 1e-4f then 
            let angle = int  <|  WHEEL_BOOSTER_MULTIPLIER  * Math.Atan2(float y, float x)
            let angle = if Math.Abs(angle) < 10 then 0
                        elif angle > 100 then 100 
                        elif angle < -100 then -100
                        else angle
            if (Math.Abs(_angle_ - angle) >= 5) then
                self.Angle <- angle
    
    let onPreferenceChanged (prefs:ISharedPreferences) =
        let addr = prefs.GetString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
        let portNumber = 4444;
        let portStr = prefs.GetString(SettingsActivity.SK_HOST_PORT, "4444");
        let (s,r) = Int32.TryParse(portStr)
        let p = if  s then r 
                else (toast <| sprintf "Incorrect port number '%s'." portStr ; portNumber)

        _transmitter.Post <| Transmitter.Connect(addr, p) 

        let showPads = prefs.GetBoolean(SettingsActivity.SK_SHOW_PADS, true)

        _pads |> List.iter (
            let padImage = 
                if showPads then
                   self.Resources.GetDrawable
                     Resource_Drawable.oxygen_actions_transform_move_icon
                else 
                    upcast new Drawables.ColorDrawable(Color.Transparent)
            fun p -> 
            (self.FindViewById<SquareTouchPadLayout> p).SetBackgroundDrawable padImage)
     
        let videoStreamURI = prefs.GetString(SettingsActivity.SK_VIDEO_URI, "");
                // --no-sout-audio --sout
                // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                // works only with vcodec=mp4v without audio :(

                // http://developer.android.com/reference/android/media/MediaPlayer.html
                // http://developer.android.com/guide/appendix/media-formats.html

        if videoStreamURI <> "" then 
            _videoStream.Uri <- videoStreamURI
            match _videoStream.StartAsync() 
              with None -> "ok" | Some s -> s
            |> sprintf "Starting video from '%s'...%s." videoStreamURI
            |> toast 

    let onCreate () = 
        self.SetContentView (Resource_Layout.activity_main)
        self.RequestedOrientation <- PM.ScreenOrientation.Landscape

        _sensorManager <- Some <| downcast self.GetSystemService(Context.SensorService)

        self.RecreateMagicButtons 5


        let tglWheel = self.FindViewById<ToggleButton> Resource_Id.tglWheel
        tglWheel.CheckedChange.Add(fun x -> _wheelEnabled <- x.IsChecked)

        let btnSettings = self.FindViewById<Button> Resource_Id.btnSettings
        btnSettings.Click.Add <| fun _ -> self.StartActivity typeof<SettingsActivity>

        //let mainLayout = self.FindViewById<_> Resource_Id.main
        //let cnt = ref 0


        let surfaceHolder = (self.FindViewById<SurfaceView> Resource_Id.video).Holder
        _videoStream.FrameReady.Add <| fun frameData -> 
                async {
                    let! bmp = Async.AwaitTask <| Android.Graphics.BitmapFactory.DecodeByteArrayAsync(frameData, 0, frameData.Length)
                    let canvas = surfaceHolder.LockCanvas()
                    return 
                        try
                            canvas.DrawBitmap(bmp, new Android.Graphics.Matrix(), null)
                        finally
                            surfaceHolder.UnlockCanvasAndPost canvas
                    
                } |> Async.RunSynchronously

            

        _videoStream.Error.Add <| fun e -> toast e.Message

        self.FindViewById<_>(Resource_Id.controlsOverlay).BringToFront()

        let vibrator = self.GetSystemService(Context.VibratorService):?>Vibrator

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
                      and  set v = _angle_ <- v; _send <| "wheel " + string v
     
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
        _videoStream.StartAsync() |> ignore 
            
    override this.OnStop() = 
        _videoStream.StopAsync()
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
