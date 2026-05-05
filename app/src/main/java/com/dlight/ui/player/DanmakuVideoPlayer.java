package com.dlight.ui.player;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.dlight.R;
import com.dlight.data.model.VodData;
import com.dlight.feature.danmaku.DanmakuOptionsFragment;
import com.dlight.util.SakuraDanmukuParser;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.ui.widget.DanmakuView;

public class DanmakuVideoPlayer extends StandardGSYVideoPlayer {
    private static final String TAG = "DanmakuVideoPlayer";

    private BaseDanmakuParser mParser;
    private IDanmakuView mDanmakuView;
    private DanmakuContext mDanmakuContext;

    private TextView mToggleDanmaku;
    private TextView mNextPlay;
    private TextView mInputSampleDanmaku;

    private long mDanmakuStartSeekPosition = -1L;
    private boolean mDanmaKuShow = true;
    private File mDanmakuFile;

    private VodData videoData;
    private ArrayList<String> videourls = new ArrayList<>();
    private Context hostContext;
    private int currentEpisode = 1;

    public DanmakuVideoPlayer(Context context) {
        super(context);
    }

    public DanmakuVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public DanmakuVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public int getLayoutId() {
        return R.layout.danmaku_layout;
    }

    @Override
    protected void init(Context context) {
        super.init(context);
        mDanmakuView = findViewById(R.id.danmaku_view);
        mToggleDanmaku = findViewById(R.id.toogle_danmaku);
        mNextPlay = findViewById(R.id.next_play);
        mInputSampleDanmaku = findViewById(R.id.inputsample_danmaku);

        if (mToggleDanmaku != null) {
            mToggleDanmaku.setOnClickListener(this);
        }
        if (mNextPlay != null) {
            mNextPlay.setOnClickListener(this);
        }
        if (mInputSampleDanmaku != null) {
            mInputSampleDanmaku.setOnClickListener(this);
        }

        initDanmaku();
    }

    public void setVideoData(@Nullable VodData videoData) {
        this.videoData = videoData;
    }

    @Nullable
    public VodData getVideoData() {
        return videoData;
    }

    public void setVideourls(@Nullable ArrayList<String> videourls) {
        this.videourls = videourls == null ? new ArrayList<>() : videourls;
        currentEpisode = Math.max(1, Math.min(currentEpisode, this.videourls.size() == 0 ? 1 : this.videourls.size()));
    }

    public ArrayList<String> getVideourls() {
        return videourls;
    }

    public void setContext(Context context) {
        this.hostContext = context;
    }

    @Nullable
    public Context getHostContext() {
        return hostContext;
    }

    public int getCurrentEpisode() {
        return currentEpisode;
    }

    public void setCurrentEpisode(int currentEpisode) {
        if (videourls == null || videourls.isEmpty()) {
            this.currentEpisode = Math.max(1, currentEpisode);
        } else {
            this.currentEpisode = Math.max(1, Math.min(currentEpisode, videourls.size()));
        }
    }

    @Override
    public boolean setUp(String url, boolean cacheWithPlay, File cachePath, String title) {
        syncEpisodeByUrl(url);
        return super.setUp(url, cacheWithPlay, cachePath, title);
    }

    @Override
    public void onPrepared() {
        super.onPrepared();
        onPrepareDanmaku(this);
    }

    @Override
    public void onVideoPause() {
        super.onVideoPause();
        danmakuOnPause();
    }

    @Override
    public void onVideoResume(boolean seek) {
        super.onVideoResume(seek);
        danmakuOnResume();
    }

    @Override
    protected void clickStartIcon() {
        super.clickStartIcon();
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            danmakuOnResume();
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            danmakuOnPause();
        }
    }

    @Override
    public void onCompletion() {
        super.onCompletion();
        releaseDanmaku(this);
    }

    @Override
    public void release() {
        releaseDanmaku(this);
        super.release();
    }

    @Override
    public void onSeekComplete() {
        super.onSeekComplete();
        long time = mProgressBar.getProgress() * getDuration() / 100;
        if (mHadPlay && getDanmakuView() != null && getDanmakuView().isPrepared()) {
            resolveDanmakuSeek(this, time);
        } else if (mHadPlay && getDanmakuView() != null) {
            setDanmakuStartSeekPosition(time);
        }
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v == null) {
            return;
        }
        int id = v.getId();
        if (id == R.id.toogle_danmaku) {
            mDanmaKuShow = !mDanmaKuShow;
            resolveDanmakuShow();
        } else if (id == R.id.next_play) {
            playNextEpisode();
        } else if (id == R.id.inputsample_danmaku) {
            inputDanmaku();
        }
    }

    @Override
    protected void cloneParams(GSYBaseVideoPlayer from, GSYBaseVideoPlayer to) {
        super.cloneParams(from, to);
        if (!(from instanceof DanmakuVideoPlayer) || !(to instanceof DanmakuVideoPlayer)) {
            return;
        }
        DanmakuVideoPlayer source = (DanmakuVideoPlayer) from;
        DanmakuVideoPlayer target = (DanmakuVideoPlayer) to;
        target.mDanmakuFile = source.mDanmakuFile;
        target.mDanmaKuShow = source.mDanmaKuShow;
        target.videoData = source.videoData;
        target.videourls = source.videourls;
        target.currentEpisode = source.currentEpisode;
    }

    @Override
    public GSYBaseVideoPlayer startWindowFullscreen(Context context, boolean actionBar, boolean statusBar) {
        GSYBaseVideoPlayer fullPlayer = super.startWindowFullscreen(context, actionBar, statusBar);
        if (!(fullPlayer instanceof DanmakuVideoPlayer)) {
            return fullPlayer;
        }

        DanmakuVideoPlayer danmakuFullPlayer = (DanmakuVideoPlayer) fullPlayer;
        danmakuFullPlayer.setDanmakuStartSeekPosition(getCurrentPositionWhenPlaying());
        danmakuFullPlayer.setDanmaKuShow(getDanmaKuShow());
        danmakuFullPlayer.setVideourls(videourls);
        danmakuFullPlayer.setVideoData(videoData);
        danmakuFullPlayer.setCurrentEpisode(currentEpisode);
        if (mDanmakuFile != null) {
            danmakuFullPlayer.setDanmaKuStream(mDanmakuFile);
        }
        return fullPlayer;
    }

    @Override
    protected void resolveNormalVideoShow(View oldF, ViewGroup vp, GSYVideoPlayer gsyVideoPlayer) {
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer);
        if (!(gsyVideoPlayer instanceof DanmakuVideoPlayer)) {
            return;
        }

        DanmakuVideoPlayer fullPlayer = (DanmakuVideoPlayer) gsyVideoPlayer;
        setDanmaKuShow(fullPlayer.getDanmaKuShow());
        setVideourls(fullPlayer.getVideourls());
        setVideoData(fullPlayer.getVideoData());
        setCurrentEpisode(fullPlayer.getCurrentEpisode());
        mDanmakuFile = fullPlayer.mDanmakuFile;

        if (getDanmakuView() != null && !getDanmakuView().isPrepared() && mDanmakuFile != null) {
            setDanmaKuStream(mDanmakuFile);
        }

        if (fullPlayer.getDanmakuView() != null && fullPlayer.getDanmakuView().isPrepared()) {
            resolveDanmakuSeek(this, fullPlayer.getCurrentPositionWhenPlaying());
            resolveDanmakuShow();
            releaseDanmaku(fullPlayer);
        } else if (mDanmakuFile != null) {
            setDanmaKuStream(mDanmakuFile);
        }
    }

    @Override
    protected void danmakuPauseOrResume(int flag) {
        if (flag == 1) {
            danmakuOnResume();
        } else if (flag == 0) {
            danmakuOnPause();
        }
    }

    public void setDanmaKuStream(File file) {
        mDanmakuFile = file;
        mParser = createParser(getIsStreamMy(file));

        if (mDanmakuView == null) {
            return;
        }

        if (mDanmakuView.isPrepared()) {
            mDanmakuView.release();
            initDanmaku();
            if (mDanmakuStartSeekPosition < 0) {
                mDanmakuStartSeekPosition = getCurrentPositionWhenPlaying();
            }
        }
        onPrepareDanmaku((DanmakuVideoPlayer) getCurrentPlayer());
    }

    private void inputDanmaku() {
        if (!(getContext() instanceof FragmentActivity)) {
            return;
        }
        if (mParser == null || mDanmakuView == null || mDanmakuContext == null) {
            Toast.makeText(getContext(), "弹幕尚未加载完成", Toast.LENGTH_SHORT).show();
            return;
        }

        DanmakuOptionsFragment fragment = DanmakuOptionsFragment.newInstance();
        fragment.setVodData(videoData);
        fragment.setVodRid(currentEpisode);
        fragment.setPostion((float) (getGSYVideoManager().getCurrentPosition() / 1000.0));
        fragment.setmDanmakuView(mDanmakuView);
        fragment.setmDanmakuContext(mDanmakuContext);
        fragment.setfontSize((17f * getDanmakuDensity()) - 0.6f);

        onVideoPause();
        FragmentActivity activity = (FragmentActivity) getContext();
        fragment.show(activity.getSupportFragmentManager(), "DanmakuOptionsFragment");
    }

    private void playNextEpisode() {
        if (videourls == null || videourls.isEmpty()) {
            Toast.makeText(getContext(), "暂无可播放剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        int nextEpisode = currentEpisode + 1;
        if (nextEpisode > videourls.size()) {
            Toast.makeText(getContext(), "已经是最后一集了", Toast.LENGTH_SHORT).show();
            return;
        }

        currentEpisode = nextEpisode;
        String nextVideoUrl = videourls.get(currentEpisode - 1);
        String title = videoData == null ? "" : (videoData.getVod_name() + "—第" + currentEpisode + "集");
        setUp(nextVideoUrl, false, null, title);
        startPlayLogic();
    }

    private void initDanmaku() {
        if (mDanmakuView == null) {
            return;
        }

        HashMap<Integer, Integer> maxLinesPair = new HashMap<>();
        maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL, 8);
        HashMap<Integer, Boolean> overlappingEnablePair = new HashMap<>();
        overlappingEnablePair.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_TOP, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_BOTTOM, true);

        mDanmakuContext = DanmakuContext.create();
        mDanmakuContext
            .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
            .setDuplicateMergingEnabled(false)
            .setScrollSpeedFactor(1.2f)
            .setScaleTextSize(1.2f)
            .setCacheStuffer(new SpannedCacheStuffer(), null)
            .setMaximumLines(maxLinesPair)
            .preventOverlapping(overlappingEnablePair);

        mDanmakuView.setCallback(new DrawHandler.Callback() {
            @Override
            public void updateTimer(DanmakuTimer timer) {
            }

            @Override
            public void drawingFinished() {
            }

            @Override
            public void danmakuShown(BaseDanmaku danmaku) {
            }

            @Override
            public void prepared() {
                IDanmakuView danmakuView = getDanmakuView();
                if (danmakuView == null) {
                    return;
                }
                danmakuView.start();
                if (getDanmakuStartSeekPosition() >= 0) {
                    resolveDanmakuSeek(DanmakuVideoPlayer.this, getDanmakuStartSeekPosition());
                    setDanmakuStartSeekPosition(-1);
                }
                resolveDanmakuShow();
            }
        });
        mDanmakuView.enableDanmakuDrawingCache(true);
    }

    private void onPrepareDanmaku(DanmakuVideoPlayer player) {
        if (player.getDanmakuView() == null || player.getDanmakuView().isPrepared()) {
            return;
        }
        if (player.getParser() == null) {
            return;
        }
        player.getDanmakuView().prepare(player.getParser(), player.getDanmakuContext());
    }

    private void resolveDanmakuSeek(DanmakuVideoPlayer player, long timeMs) {
        if (!mHadPlay || player.getDanmakuView() == null || !player.getDanmakuView().isPrepared()) {
            return;
        }
        player.getDanmakuView().seekTo(timeMs);
    }

    private void resolveDanmakuShow() {
        post(() -> {
            if (getDanmakuView() == null) {
                return;
            }
            if (mDanmaKuShow) {
                if (!getDanmakuView().isShown()) {
                    getDanmakuView().show();
                }
                if (mToggleDanmaku != null) {
                    mToggleDanmaku.setText("弹幕关");
                }
            } else {
                if (getDanmakuView().isShown()) {
                    getDanmakuView().hide();
                }
                if (mToggleDanmaku != null) {
                    mToggleDanmaku.setText("弹幕开");
                }
            }
        });
    }

    private void danmakuOnPause() {
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
    }

    private void danmakuOnResume() {
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            mDanmakuView.resume();
        }
    }

    private void releaseDanmaku(@Nullable DanmakuVideoPlayer player) {
        if (player == null || player.getDanmakuView() == null) {
            return;
        }
        Debuger.printfError("release Danmaku!");
        player.getDanmakuView().release();
    }

    @Nullable
    private InputStream getIsStreamMy(@Nullable File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream in = new FileInputStream(file);
             InputStreamReader inputReader = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputReader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            return new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "read danmaku file error: " + e.getMessage(), e);
            return null;
        }
    }

    private BaseDanmakuParser createParser(@Nullable InputStream stream) {
        if (stream == null) {
            return new BaseDanmakuParser() {
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }

        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            Log.e(TAG, "load danmaku stream error: " + e.getMessage(), e);
            return new BaseDanmakuParser() {
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }

        SakuraDanmukuParser parser = new SakuraDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }

    private void syncEpisodeByUrl(@Nullable String targetUrl) {
        if (TextUtils.isEmpty(targetUrl) || videourls == null || videourls.isEmpty()) {
            return;
        }
        String normalized = targetUrl.trim();
        for (int i = 0; i < videourls.size(); i++) {
            String item = videourls.get(i);
            if (TextUtils.isEmpty(item)) {
                continue;
            }
            if (normalized.equals(item.trim())) {
                currentEpisode = i + 1;
                return;
            }
        }
    }

    private float getDanmakuDensity() {
        BaseDanmakuParser parser = getParser();
        if (parser == null || parser.getDisplayer() == null) {
            return 1.0f;
        }
        return parser.getDisplayer().getDensity();
    }

    public BaseDanmakuParser getParser() {
        if (mParser == null && mDanmakuFile != null) {
            mParser = createParser(getIsStreamMy(mDanmakuFile));
        }
        return mParser;
    }

    public DanmakuContext getDanmakuContext() {
        return mDanmakuContext;
    }

    public IDanmakuView getDanmakuView() {
        return mDanmakuView;
    }

    public long getDanmakuStartSeekPosition() {
        return mDanmakuStartSeekPosition;
    }

    public void setDanmakuStartSeekPosition(long danmakuStartSeekPosition) {
        this.mDanmakuStartSeekPosition = danmakuStartSeekPosition;
    }

    public void setDanmaKuShow(boolean danmaKuShow) {
        mDanmaKuShow = danmaKuShow;
        resolveDanmakuShow();
    }

    public boolean getDanmaKuShow() {
        return mDanmaKuShow;
    }

}
