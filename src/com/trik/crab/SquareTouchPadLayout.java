package com.trik.crab;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class SquareTouchPadLayout extends RelativeLayout {

    final int mDefaultSize = 100;

    public SquareTouchPadLayout(Context context) {
        super(context);
    }

    public SquareTouchPadLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareTouchPadLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int halfPerimeter = width + height;
        final int size =
                width * height != 0 ? Math.min(width, height) : halfPerimeter == 0 ? mDefaultSize : halfPerimeter;
        setMeasuredDimension(size, size);
    };

}
