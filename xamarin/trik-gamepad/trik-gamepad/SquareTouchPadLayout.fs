namespace trik.gamepad
open Android.Widget
open Android.Content
open Android.Views

type SquareTouchPadLayout =
    inherit RelativeLayout

    new(context) = { inherit RelativeLayout(context) }
    new(context, attrs) =  { inherit RelativeLayout((context:Context), attrs) }
    new(context, attrs, defStyle) = { inherit RelativeLayout(context, attrs, defStyle) }

    override this.OnMeasure(widthMeasureSpec, heightMeasureSpec) =
        //???? base.OnMeasure(widthMeasureSpec, heightMeasureSpec)
        let width = View.MeasureSpec.GetSize(widthMeasureSpec)
        let height = View.MeasureSpec.GetSize(heightMeasureSpec)
        let halfPerimeter = width + height
        let size = if width * height = 0 then min width height 
                   elif halfPerimeter = 0  then 100 
                   else halfPerimeter
        this.SetMeasuredDimension(size, size)
