package com.dlight.feature.danmaku;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.model.VodData;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.HashMap;
import java.util.Map;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;
import retrofit2.Call;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class DanmakuOptionsFragment extends BottomSheetDialogFragment {

    public static DanmakuOptionsFragment newInstance() {
        return new DanmakuOptionsFragment();
    }
    private VodData vodData;
    private EditText inputDanmaku;
    private Button sendButton;
    private RadioGroup typeGroup;      // 弹幕类型选择
    private String selectedColor = "#FFFFFF";   // 默认颜色
    private int selectedType ;        // 默认位置

    private int vodRid;
    private float postion;

    private IDanmakuView mDanmakuView;//弹幕view
    private DanmakuContext mDanmakuContext;

    private float fontSize;
    private Map<Integer, Integer> conversionMap = new HashMap<>();
    @Override
    public void onStart() {
        //重写启动方式 强制在播放全面界面全部显示，优化用户体验
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 将 200dp 转换为 px
                int heightInDp = 200;
                float scale = getResources().getDisplayMetrics().density;
                int heightInPx = (int) (heightInDp * scale + 0.5f);  // dp → px 转换

                // 设置高度
                bottomSheet.getLayoutParams().height = heightInPx;

                // 2. 强制展开 BottomSheetDialog
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);  // 展开到全屏
            }
        }
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_danmakuoptions, container, false);

        inputDanmaku = view.findViewById(R.id.input_danmaku);
        sendButton = view.findViewById(R.id.btn_send_danmaku);
        typeGroup = view.findViewById(R.id.type_group);

        // 颜色按钮绑定事件
        setupColorSelection(view);

        // 弹幕类型选择事件
        setupTypeSelection();

        // 发送按钮点击事件
        sendButton.setOnClickListener(v -> sendDanmaku());

        conversionMap.put(0, 1);
        conversionMap.put(1, 5);
        conversionMap.put(2, 4);
        return view;
    }

    /**
     * 初始化颜色选择按钮
     */
    private void setupColorSelection(View view) {
        int[] colorButtons = {
            R.id.color_red, R.id.color_blue, R.id.color_green,
            R.id.color_yellow, R.id.color_white, R.id.color_purple, R.id.color_black
        };

        // 遍历绑定颜色选择
        for (int buttonId : colorButtons) {
            Button colorButton = view.findViewById(buttonId);
            colorButton.setOnClickListener(v -> {
                selectedColor = getButtonColor(colorButton.getId());
                highlightSelectedColor(view, colorButton);
            });
        }
    }

    /**
     * 根据按钮 ID 获取颜色值
     */
    private String getButtonColor(int buttonId) {
        switch (buttonId) {
            case R.id.color_red: return "#e54256";
            case R.id.color_blue: return "#39ccff";
            case R.id.color_green: return "#64DD17";
            case R.id.color_yellow: return "#ffe133";
            case R.id.color_white: return "#FFFFFF";
            case R.id.color_purple: return "#D500F9";
            case R.id.color_black: return "#000000";
            default: return "#FFFFFF";
        }
    }

    /**
     * 高亮当前选中的颜色按钮
     */
    private void highlightSelectedColor(View view, Button selectedButton) {
        int[] colorButtons = {
            R.id.color_red, R.id.color_blue, R.id.color_green,
            R.id.color_yellow, R.id.color_white, R.id.color_purple, R.id.color_black
        };

        // 重置所有按钮的透明度
        for (int buttonId : colorButtons) {
            Button button = view.findViewById(buttonId);
            button.setAlpha(0.5f);  // 非选中状态半透明
        }

        // 高亮当前选中颜色
        selectedButton.setAlpha(1.0f);
    }

    /**
     * 选择弹幕类型
     */
    private void setupTypeSelection() {
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.type_top) {
                selectedType = 1;
            } else if (checkedId == R.id.type_scroll) {
                selectedType = 0;
            } else if (checkedId == R.id.type_bottom) {
                selectedType = 2;
            }
        });
    }

    /**
     * 发送弹幕
     */
    private void sendDanmaku() {

        String content = inputDanmaku.getText().toString().trim();
        if (content.isEmpty()) {
            inputDanmaku.setError("弹幕内容不能为空！");
            return;
        }
        Context context=getContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        // 检查是否已经登录
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
        if (!isLoggedIn) {
            Toast.makeText(context, "请先登录账户！", Toast.LENGTH_SHORT).show();
            return;  // 用户未登录，返回 null
        }
        String token = AuthHeaderUtil.bearer(sharedPreferences.getString("token", ""));
        int useId=sharedPreferences.getInt("Id", 0);
        int color =parseColor(selectedColor);

        if(color==-1){
            return;
        }
        int type = conversionMap.get(selectedType);
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonResModel> call = apiService.sendDanmaku(
            vodData.getVod_id(),
            vodRid,
            content,
            postion,
            color,
            selectedType,
            token
        );

        //画一个弹幕
        addDanmaku(true,content,color,type);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                Log.d("GetComment", "msg" + data);
                Toast.makeText(context, "发送成功！", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(String error) {
                Log.e("GetComment", "Error: " + error);
                Toast.makeText(context, "发送失败！", Toast.LENGTH_SHORT).show();

            }
        });
        // 关闭 Fragment
        getParentFragmentManager().beginTransaction().remove(this).commit();
    }

    /**
     模拟添加弹幕数据
     */
    private void addDanmaku(boolean islive,String content,int color,int type) {
        BaseDanmaku danmaku = mDanmakuContext.mDanmakuFactory.createDanmaku(type);
        if (danmaku == null || mDanmakuView == null) {
            return;
        }
        danmaku.text = content;
        danmaku.padding = 5;
        danmaku.priority = 8;  // 可能会被各种过滤器过滤并隐藏显示，所以提高等级
        danmaku.isLive = islive;
        danmaku.setTime(mDanmakuView.getCurrentTime() + 500);
        danmaku.textSize = fontSize;
        danmaku.textColor = color;
        //danmaku.textShadowColor = Color.WHITE; 白色文字描边
        danmaku.borderColor = Color.GREEN;
        //往Danmakus里面添加一条弹幕
        mDanmakuView.addDanmaku(danmaku);

    }
    public void setVodData(VodData vodData){
        this.vodData=vodData;
    }
    public void setVodRid(int vodRid){
        this.vodRid=vodRid;
    }
    public  void setPostion(float postion){
        this.postion=postion;
    }
    public void setmDanmakuView(IDanmakuView danmakuView){
        this.mDanmakuView=danmakuView;
    }
    public void setmDanmakuContext(DanmakuContext danmakuContext){
        this.mDanmakuContext=danmakuContext;
    }
    public  void setfontSize(float  fontSize){
        this.fontSize=fontSize;
    }
    private int parseColor(String colorString) {
        // 移除颜色字符串的 # 字符
        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);
        }
        // 检查颜色字符串是否是6位
        if (colorString.length() == 6) {
            // 解析RGB并返回 int 类型的颜色值
            try {
                return (int) Long.parseLong(colorString, 16) | 0xFF0000;
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return -1;  // 返回 -1 如果发生解析错误
            }
        }
        return -1;  // 返回 -1 如果颜色字符串不合法
    }
}
