package com.dlight.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;

import retrofit2.Call;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;


public class AnnouncementBar extends Fragment {
    TextView annoText;

    String textContent;
    //由于使用add 第一次添加的时候会触发onStart 后面使用的是hide 和show 所以这个onStart不会触发

    public void onShow() {
        // 这里是你希望在 onStart() 执行的代码
        //TextView 可能已经在之前的状态下滚动了，但没有重新启动滚动行为。通过先取消选中状态，再重新选中，就像是重新激活了它的动画效果。
        annoText.setSelected(false); // 先设置为 false 解决从动漫页面返回没有激活滚动的效果
        annoText.setSelected(true);
    }
//    @Override
//    public void onStart(){
//        super.onStart();
//        annoText.setSelected(true);
//        Log.d("AnnouncementBar", "onStart: ");
//    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_announcement_bar, container, false);

        annoText=view.findViewById(R.id.announcement_text);

        annoText.setOnClickListener(v->anno());

        getAnnoText();
        annoText.setText(textContent);
        annoText.setSelected(true);
        return view;
    }

    private void getAnnoText(){

        // 创建 Retrofit 实例
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonResModel> call = apiService.requestAnnoData();

        // 调用封装的 ApiClient 来处理请求
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                if(data.isSuccessCode()) {
                    textContent = data.getData();
                    annoText.setText(textContent);
                    Log.d("getAnnoText", "onSuccess: ");
                }
            }

            @Override
            public void onFailure(String error) {
                Log.d("getAnnoText", "onFailure: "+error);
            }
        });


    }
    private void anno(){
        new AlertDialog.Builder(requireContext())
            .setTitle("公告")
            .setMessage(textContent == null ? "" : textContent)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
}
