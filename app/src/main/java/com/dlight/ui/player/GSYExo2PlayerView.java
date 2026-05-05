package com.dlight.ui.player;

import android.content.Context;
import android.util.AttributeSet;

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

public class GSYExo2PlayerView extends StandardGSYVideoPlayer {

    public GSYExo2PlayerView(Context context) {
        super(context);
    }

    public GSYExo2PlayerView(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public GSYExo2PlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
