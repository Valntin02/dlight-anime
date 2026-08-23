package com.dlight.feature.comment;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

public class CommentSheetLayout extends ConstraintLayout {
    public static final int MAX_HEIGHT_DP = 600;

    public CommentSheetLayout(Context context) {
        super(context);
    }

    public CommentSheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommentSheetLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = Math.round(
            MAX_HEIGHT_DP * getResources().getDisplayMetrics().density
        );
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int availableHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        int targetHeight = heightMode == View.MeasureSpec.UNSPECIFIED
            ? maxHeight
            : Math.min(availableHeight, maxHeight);

        super.onMeasure(
            widthMeasureSpec,
            View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY)
        );
    }
}
