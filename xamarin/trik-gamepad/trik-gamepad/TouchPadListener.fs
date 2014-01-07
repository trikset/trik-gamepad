namespace com.trik.gamepad
open System
open Android.Util
open Android.Views


type TouchPadListener (pad:View, padName:string, sender:SenderService) =

    let mutable prevX = 0
    let mutable prevY = 0
    let onTouch (event:MotionEvent) = 
        match event.Action with 
            | MotionEventActions.Up -> sender.Send(padName + " up")
            | MotionEventActions.Down
            | MotionEventActions.Move ->
                let aX = event.GetX()  
                let aY = event.GetY()
                let maxX = float32 pad.Width
                let maxY = float32 pad.Height
                let SENSITIVITY = 3

                let SCALE = 1.15f
                let rX = int (200.0f * SCALE * (aX / maxX - 0.5f))
                let rY = int (-200.0f * SCALE * (aY / maxY - 0.5f))
                let curY = Math.Max(-100, Math.Min(rY, 100))
                let curX = Math.Max(-100, Math.Min(rX, 100))

                if (Math.Abs(curX - prevX) > SENSITIVITY || Math.Abs(curY - prevY) > SENSITIVITY) then
                    prevX <- curX
                    prevY <- curY
                    sender.Send(padName + " " + curX.ToString() + " " + curY.ToString())
            | _ -> Log.Error("TouchEvent", "Unknown: {0}", event.ToString()) |> ignore
    
    member x.OnTouch(v:View, event:MotionEvent) = 
        if (v <> pad) then false 
        else onTouch event; true

                
 