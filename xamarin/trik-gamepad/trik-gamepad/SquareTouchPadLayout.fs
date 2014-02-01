namespace com.trik.gamepad
open Android.Widget
open Android.Content
open Android.Views
open System

type SquareTouchPadLayout(context, attrs, defStyle) as self =
    inherit ImageView(context, attrs, defStyle)
    let event = new Event<_>() 
    let mutable prevX = -100
    let mutable prevY = prevX
            
    do self.Touch.Add <| fun args -> 
        let motionEvent = args.Event
        match motionEvent.Action with 
            | MotionEventActions.Up -> event.Trigger (MotionEventActions.Up, (prevX, prevY))
            | MotionEventActions.Down 
            | MotionEventActions.Move as mea ->
                let SENSITIVITY = 3
                let SCALE = 1.15
                
                let aX, aY = motionEvent.GetX(), motionEvent.GetY()
                let maxX, maxY = float self.Width, float self.Height
                let inline normalize z maxZ = 
                    200.0 * SCALE * (float z / maxZ - 0.5) |> int
                    |> max -100 |> min 100 

                let curY = - normalize aY maxY
                let curX = normalize aX maxX

                if (mea = MotionEventActions.Down || Math.Abs(curX - prevX) > SENSITIVITY || Math.Abs(curY - prevY) > SENSITIVITY) then
                    prevX <- curX
                    prevY <- curY
                    event.Trigger (mea, (prevX, prevY))
            | _ -> Android.Util.Log.Error("TouchEvent", "Unknown: {0}", event) |> ignore   
   
    new(context:Context, attrs) =  new SquareTouchPadLayout(context, attrs, 0)       

    [<CLIEvent>]
    member this.PadActivity =  event.Publish

    override this.OnMeasure(widthMeasureSpec, heightMeasureSpec) =
        //base.OnMeasure(widthMeasureSpec, heightMeasureSpec)
        let width = View.MeasureSpec.GetSize(widthMeasureSpec)
        let height = View.MeasureSpec.GetSize(heightMeasureSpec)
        let size = max this.SuggestedMinimumHeight <| min width height            
        this.SetMeasuredDimension(size, size)

    override this.SuggestedMinimumWidth =  this.SuggestedMinimumHeight
    override this.SuggestedMinimumHeight =  List.max <| 100:: if this.Background <> null then
                                                                 [this.Background.MinimumWidth
                                                                  this.Background.MinimumHeight]
                                                              else []