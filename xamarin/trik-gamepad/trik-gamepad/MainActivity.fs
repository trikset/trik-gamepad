namespace trik.gamepad

open System

open Android.App
open Android.Content
open Android.OS
open Android.Runtime
open Android.Views
open Android.Widget
open Android.Hardware
open Android.Util

[<Activity (Label = "trik-gamepad", MainLauncher = true)>]
type MainActivity () as self =
  
    inherit Activity ()
    let mutable _sensorManager: SensorManager option = None
    let mutable _wheelEnabled = false
    let mutable _angle = 0
    let mutable _sender: SenderService option = None
    let mutable _video: VideoView option = None

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
                _sender.Value.Send("wheel " + string angle)
       
    override this.OnCreate (bundle) =

        base.OnCreate (bundle)

        // Set our view from the "main" layout resource
        this.SetContentView (Resource_Layout.activity_main)
        this.RequestedOrientation <- PM.ScreenOrientation.Landscape

        _sensorManager <- Some <| downcast this.GetSystemService(Context.SensorService)
        _sender <- Some <| new SenderService(this)

        this.RecreateMagicButtons 5


        _sender.Value.Disconnected.Add <| fun e -> toast("Disconnected." + e.ToString())

        let tglWheel = this.FindViewById<ToggleButton> Resource_Id.tglWheel
        tglWheel.CheckedChange.Add(fun x -> lock this <| fun () -> _wheelEnabled <- x.IsChecked)

        let btnSettings = this.FindViewById<Button>(Resource_Id.btnSettings)
        btnSettings.Click.Add <| fun _ -> this.StartActivity typeof<SettingsActivity>

 
        _video <- Some <| this.FindViewById<VideoView>(Resource_Id.video);
        _video.Value.Error.Add <| fun e ->
                let errorStr = sprintf "What = %d, extra = %d" e.What e.Extra
                Log.Error("VIDEO", errorStr) |> ignore
                toast("Error playing video stream " + errorStr);
                // mVideo.stopPlayback();
                // mVideo.setBackgroundColor(Color.TRANSPARENT);
       
        _video.Value.Completion.Add <| fun e ->
                Log.i("VIDEO", "End of video stream encountered.")
                _video.Value.Resume() // TODO: Keep-alive instead of this hack
           

        _video.Value.Prepared.Add <| fun e -> 
                // TODO: Stretch/scale video
                //e.Looping <- true // TODO: Doesn't work :(
                _video.Value.Start()
          
        // video starts playing after URI is read from prefs later
   

        this.FindViewById<_>(Resource_Id.controlsOverlay).BringToFront()

        let createHandler (pad, name, sender) = 
            let l = new TouchPadListener (pad, name, sender)
            fun (e:View.TouchEventArgs) -> l.OnTouch(pad, e.Event) |> ignore
        let pad1 = this.FindViewById<_>(Resource_Id.leftPad)
        pad1.Touch.Add <| createHandler (pad1, "pad 1", _sender.Value)

        let pad2 = this.FindViewById<_>(Resource_Id.rightPad)
        pad2.Touch.Add <| createHandler (pad2, "pad 2", _sender.Value)

        let prefs = Android.Preferences.PreferenceManager.GetDefaultSharedPreferences 
                        this.BaseContext
        let h evt  =
            let addr = prefs.GetString(SettingsActivity.SK_HOST_ADDRESS, "127.0.0.1");
            let portNumber = 4444;
            let portStr = prefs.GetString(SettingsActivity.SK_HOST_PORT, "4444");
            let (s,r) = Int32.TryParse(portStr)
            if not s then toast <| sprintf "Incorrect port number '%s'." portStr ; portNumber
            else r
            |> fun p -> _sender.SetTarget(addr, p)

            let showPads = prefs.GetBoolean(SettingsActivity.SK_SHOW_PADS, true)
            let padImage = if showPads then this.Resources.Drawable 
                                                 Resource_Drawable.oxygen_actions_transform_move_icon
                           else new ColorDrawable(Color.TRANSPARENT)
            pad1.SetBackgroundDrawable padImage
            pad2.SetBackgroundDrawable padImage
            let videoStreamURI = prefs.GetString(SettingsActivity.SK_VIDEO_URI, "");
                    // --no-sout-audio --sout
                    // "#transcode{width=320,height=240,vcodec=mp2v,fps=20}:rtp{ttl=5,sdp=rtsp://:8889/s}"
                    // works only with vcodec=mp4v without audio :(

                    // http://developer.android.com/reference/android/media/MediaPlayer.html
                    // http://developer.android.com/guide/appendix/media-formats.html

            let  videoURI = Uri.Parse videoStreamURI

            if videoURI <> null then 
                toast <| "Starting video from '" + videoStreamURI + "'."
                _video.VideoURI mVideoURI
   
        h <| new SharedPreferencesOnSharedPreferenceChangeEventArgs(SettingsActivity.SK_HOST_ADDRESS)
        prefs.SharedPreferenceChange.Add h

      
    override this.OnResume() = 
        base.OnResume()
        _sensorManager.Value.RegisterListener(this:>ISensorEventListener, 
                                              _sensorManager.Value.GetDefaultSensor SensorType.All,
                                              SensorDelay.Normal) |> ignore 
        _video.Value.Resume()
            // send current config
            // final float hsv[] = new float[3];
            // Color.colorToHSV(PreferenceManager.getDefaultSharedPreferences(this)
            // .getInt("targetColor", 0), hsv);
            // final String hsvRepr = "H:" + hsv[0] + " S:" + hsv[1] + " V:" +
            // hsv[2];
            // mSender.send("config targetColor=\"" + hsvRepr + "\"");
        
    override this.OnStop() = 
        _video.Value.StopPlayback()
        _sensorManager.Value.UnregisterListener(this:>ISensorEventListener)
        base.OnStop()


    
    member this.RecreateMagicButtons(count) = 
        let buttonsView = this.FindViewById<ViewGroup>(Resource_Id.buttons)
        buttonsView.RemoveAllViews()
        for i in 1..count do
            let name = string i
            let btn = new Button(this, Gravity = GravityFlags.Center, Text = name)
            btn.SetPadding(10, 10, 10, 10)
            btn.Click.Add <| fun e -> _sender.Value.Send("btn " + name + " down") // TBD: "up"                                                                 
            buttonsView.AddView(btn);
        
    interface ISensorEventListener with
        member x.OnAccuracyChanged (sensor, accuracy) = ()
        member x.OnSensorChanged (event) = 
            if event.Sensor.Type = SensorType.Accelerometer then
                if _wheelEnabled then 
                    processSensor event.Values

            else
                Log.Info("Sensor", event.Sensor.Type.ToString()) |> ignore
        

