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
    let mutable _angle = 0
    let mutable _video: VideoView = null
    let mutable _send = fun _ -> ()
    let mutable _pad1: View = null
    let mutable _pad2: View = null
    let mutable _transmitter: MailboxProcessor<_> option = None

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

            if (Math.Abs(_angle - angle) >= 7) then
                _angle <- angle
                _send <| "wheel " + string angle
       
    override this.OnCreate (bundle) =

        base.OnCreate (bundle)

        // Set our view from the "main" layout resource
        this.SetContentView (Resource_Layout.activity_main)
        this.RequestedOrientation <- PM.ScreenOrientation.Landscape

        _sensorManager <- Some <| downcast this.GetSystemService(Context.SensorService)

        this.RecreateMagicButtons 5


        let tglWheel = this.FindViewById<ToggleButton> Resource_Id.tglWheel
        tglWheel.CheckedChange.Add(fun x -> lock this <| fun () -> _wheelEnabled <- x.IsChecked)

        let btnSettings = this.FindViewById<Button>(Resource_Id.btnSettings)
        btnSettings.Click.Add <| fun _ -> this.StartActivity typeof<SettingsActivity>

 
        _video <- this.FindViewById<VideoView>(Resource_Id.video)
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
   

        this.FindViewById<_>(Resource_Id.controlsOverlay).BringToFront()

        let createHandler (pad, name) = 
            let l = new TouchPadListener (pad, name)
            l.Activated.Add _send
            fun (e:View.TouchEventArgs) -> l.OnTouch(pad, e.Event) |> ignore
        _pad1 <- this.FindViewById<_>(Resource_Id.leftPad)
        _pad1.Touch.Add <| createHandler (_pad1, "pad 1")

        _pad2 <- this.FindViewById<_>(Resource_Id.rightPad)
        _pad2.Touch.Add <| createHandler (_pad2, "pad 2")

        let prefs = Android.Preferences.PreferenceManager.GetDefaultSharedPreferences Application.Context
        prefs.RegisterOnSharedPreferenceChangeListener this
        (this:>ISharedPreferencesOnSharedPreferenceChangeListener).OnSharedPreferenceChanged(prefs, null)
      
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
        member this.OnSharedPreferenceChanged (prefs, _) =  
            let addr = prefs.GetString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
            let portNumber = 4444;
            let portStr = prefs.GetString(SettingsActivity.SK_HOST_PORT, "4444");
            let (s,r) = Int32.TryParse(portStr)
            if not s then toast <| sprintf "Incorrect port number '%s'." portStr ; portNumber
            else r
            |> fun p -> 
                    if _transmitter.IsSome then
                        (_transmitter.Value :> IDisposable).Dispose()
                    _transmitter <- Some <| Transmitter.create this (addr, p) 
                    _send <-  _transmitter.Value.Post << Transmitter.Message.Send

            let showPads = prefs.GetBoolean(SettingsActivity.SK_SHOW_PADS, true)
            let padImage = 
                if showPads then
                   this.Resources.GetDrawable Resource_Drawable.oxygen_actions_transform_move_icon
                else 
                    upcast new Drawables.ColorDrawable(Color.Transparent)
            _pad1.SetBackgroundDrawable padImage
            _pad2.SetBackgroundDrawable padImage
            let videoStreamURI = prefs.GetString(SettingsActivity.SK_VIDEO_URI, "");
                    // --no-sout-audio --sout
                    // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                    // works only with vcodec=mp4v without audio :(

                    // http://developer.android.com/reference/android/media/MediaPlayer.html
                    // http://developer.android.com/guide/appendix/media-formats.html

            if videoStreamURI <> "" then 
                toast <| "Starting video from '" + videoStreamURI + "'."
                _video.SetVideoURI <| Android.Net.Uri.Parse videoStreamURI
    
    interface IJavaObject with
        member this.Handle = this.Handle
