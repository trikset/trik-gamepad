package com.trik.gamepad;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class SquareTouchPadLayout extends RelativeLayout {

    final static int sDefaultSize = 100;

    public SquareTouchPadLayout(final Context context) {
        super(context);
    }

    public SquareTouchPadLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareTouchPadLayout(final Context context,
            final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec,
            final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int halfPerimeter = width + height;
        final int size = width * height != 0 ? Math.min(width, height)
                : halfPerimeter != 0 ? halfPerimeter
                        : SquareTouchPadLayout.sDefaultSize;
        setMeasuredDimension(size, size);
    };

}
