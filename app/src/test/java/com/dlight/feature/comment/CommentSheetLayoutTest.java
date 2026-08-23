package com.dlight.feature.comment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dlight.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CommentSheetLayoutTest {
    private Context context;
    private ViewGroup root;
    private View title;
    private View recycler;
    private View input;

    @Before
    public void setUp() {
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme);
        root = (ViewGroup) LayoutInflater.from(context)
            .inflate(R.layout.fragment_comment_bottom_sheet, null, false);
        title = root.findViewById(R.id.tv_title);
        recycler = root.findViewById(R.id.comment_sub_recycler);
        input = root.findViewById(R.id.comment_input_layout);
    }

    @Test
    public void tallParentCapsSheetAndLeavesRoomForList() {
        measureAndLayout(dp(800));

        assertEquals(dp(600), root.getMeasuredHeight());
        assertTrue(recycler.getMeasuredHeight() > 0);
        assertVisibleAndMeasured(title);
        assertVisibleAndMeasured(input);
    }

    @Test
    public void shortParentShrinksListAndKeepsChromeVisible() {
        measureAndLayout(dp(800));
        int tallListHeight = recycler.getMeasuredHeight();

        measureAndLayout(dp(400));

        assertEquals(dp(400), root.getMeasuredHeight());
        assertTrue(recycler.getMeasuredHeight() > 0);
        assertTrue(recycler.getMeasuredHeight() < tallListHeight);
        assertVisibleAndMeasured(title);
        assertVisibleAndMeasured(input);
    }

    private void measureAndLayout(int parentHeight) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(dp(360), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY);
        root.measure(widthSpec, heightSpec);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private void assertVisibleAndMeasured(View view) {
        assertEquals(View.VISIBLE, view.getVisibility());
        assertTrue(view.getMeasuredHeight() > 0);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
