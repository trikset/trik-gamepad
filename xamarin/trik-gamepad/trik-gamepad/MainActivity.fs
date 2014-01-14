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
    let mutable _video: VideoView = null
    let _transmitter = Transmitter.create self
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
            toast <| "Starting video from '" + videoStreamURI + "'."
            _video.SetVideoURI <| Android.Net.Uri.Parse videoStreamURI

    let onCreate () = 
        self.SetContentView (Resource_Layout.activity_main)
        self.RequestedOrientation <- PM.ScreenOrientation.Landscape

        _sensorManager <- Some <| downcast self.GetSystemService(Context.SensorService)

        self.RecreateMagicButtons 5


        let tglWheel = self.FindViewById<ToggleButton> Resource_Id.tglWheel
        tglWheel.CheckedChange.Add(fun x -> _wheelEnabled <- x.IsChecked)

        let btnSettings = self.FindViewById<Button>(Resource_Id.btnSettings)
        btnSettings.Click.Add <| fun _ -> self.StartActivity typeof<SettingsActivity>

 
        _video <- self.FindViewById<VideoView>(Resource_Id.video)
        _video.Error.Add <| fun e ->
                let errorStr = sprintf "What = %A, extra = %A" e.What e.Extra
                Log.Error("VIDEO", errorStr) |> ignore
                toast("Error playing video stream " + errorStr)
                // mVideo.stopPlayback()
                // mVideo.setBackgroundColor(Color.TRANSPARENT)
       
        _video.Completion.Add <| fun e ->
                Log.Info("VIDEO", "End of video stream encountered.") |> ignore
                _video.Resume() // TODO: Keep-alive instead of this hack
           

        _video.Prepared.Add <| fun e -> 
                // TODO: Stretch/scale video
                //e.Looping <- true // TODO: Doesn't work :(
                _video.Start()
          
        // video starts playing after URI is read from prefs later
   

        self.FindViewById<_>(Resource_Id.controlsOverlay).BringToFront()

        _pads |> List.iteri  (fun i id ->  
            let send a b = _send <| sprintf "pad %d %O %O" (i+1) a b
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
        _video.Resume()
            // send current config
            // final float hsv[] = new float[3];
            // Color.colorToHSV(PreferenceManager.getDefaultSharedPreferences(this)
            // .getInt("targetColor", 0), hsv);
            // final String hsvRepr = "H:" + hsv[0] + " S:" + hsv[1] + " V:" +
            // hsv[2];
            // mSender.send("config targetColor=\"" + hsvRepr + "\"");
        
    override this.OnStop() = 
        _video.StopPlayback()
        _sensorManager.Value.UnregisterListener(this)
        base.OnStop()
    
    member this.RecreateMagicButtons(count) = 
        let buttonsView = this.FindViewById<ViewGroup>(Resource_Id.buttons)
        buttonsView.RemoveAllViews()
        for i in 1..count do
            let name = string i
            let btn = new Button(this, Gravity = GravityFlags.Center, Text = name)
            btn.SetPadding(10, 10, 10, 10)
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
