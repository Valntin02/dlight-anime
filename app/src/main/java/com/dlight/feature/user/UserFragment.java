package com.dlight.feature.user;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;


import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dlight.R;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.util.Param;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import com.dlight.data.local.MyStarActivity;
import com.dlight.data.local.PlayRecordActivity;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;
import com.dlight.feature.download.ActvityDownVideo;
public class UserFragment extends Fragment implements View.OnClickListener {
    private TextView tvUsername;
    private ShapeableImageView ivAvatarFile;  // 修改为 ShapeableImageView

    private View btnHistory, btnStar, btnDownload, btnSettings;
    private String TAG = "user img";
    private boolean urlFlag = false;
    private OnLogoutListener logoutListener;
    //用来处理退出回调
    public interface OnLogoutListener {
        void Logout();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_user, container, false);

        tvUsername = rootView.findViewById(R.id.user_name);
        ivAvatarFile = rootView.findViewById(R.id.avatar_file);  // 获取 ShapeableImageView
        btnHistory = rootView.findViewById(R.id.btn_history);
        btnDownload = rootView.findViewById(R.id.btn_download);
        btnStar = rootView.findViewById(R.id.btn_star);
        btnSettings = rootView.findViewById(R.id.btn_settings);
        logoutListener = (OnLogoutListener) getParentFragment();

        btnHistory.setOnClickListener(this);
        btnDownload.setOnClickListener(this);
        btnStar.setOnClickListener(this);
        btnSettings.setOnClickListener(this);
        ivAvatarFile.setOnClickListener(this);

        avatarRefresh(false, null);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 用户从设置页退出登录后回到这里，立刻切回登录页
        SharedPreferences sp = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        if (!sp.getBoolean("is_logged_in", false) && logoutListener != null) {
            logoutListener.Logout();
        }
    }


    private  void avatarRefresh(boolean flag,String url){
        String baseurl = Param.getInstance().getBaseUrl();

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // 检查用户是否已登录
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);

        //服务器修改了头像url
        if(flag) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("avatarFile", url);
            editor.apply();  // 保存数据
        }

        if (isLoggedIn) {
            String username = sharedPreferences.getString("username", "");
            String avatarFile = sharedPreferences.getString("avatarFile", "");
            Log.d("avatarRefresh", "avatarRefresh: "+avatarFile);

            if(avatarFile.isEmpty()) urlFlag=true;
            else urlFlag=false;

            if (avatarFile != null && avatarFile.startsWith("http")) {
                baseurl = rewriteLoopbackHost(avatarFile.trim(), baseurl);
            } else {
                baseurl += avatarFile == null ? "" : avatarFile.trim();
            }

            Log.d("userinfo", baseurl);

            tvUsername.setText(username);


        // 使用 Glide 加载头像并设置到 ShapeableImageView
        //这里缓存问题掉了半天 原来是  .diskCacheStrategy(DiskCacheStrategy.ALL) 这个属性一直没有去掉
        //导致一直不能刷新图片
        Glide.with(getContext())
            .load(baseurl)
            .circleCrop()  // 通过 Glide 的 circleCrop() 方法实现圆形裁剪
            .skipMemoryCache(true) // 跳过内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(R.drawable.button_style)
            .into(ivAvatarFile);  // 设置图片
    }
}

// 后端会把头像 URL 写死成 127.0.0.1/localhost（它自己机器上的回环地址），
// 但模拟器里的 127.0.0.1 是模拟器自身——所以遇到这种 host 时改写成 Param 的 baseUrl host
// (模拟器走 10.0.2.2，真机走 DEVICE_BASE_URL)。
private static String rewriteLoopbackHost(String url, String baseUrl) {
    if (url == null) return null;
    try {
        Uri u = Uri.parse(url);
        String host = u.getHost();
        if ("127.0.0.1".equals(host) || "localhost".equals(host) || "0.0.0.0".equals(host)) {
            Uri base = Uri.parse(baseUrl);
            String authority = base.getEncodedAuthority();
            String scheme = base.getScheme();
            if (authority != null && scheme != null) {
                return u.buildUpon().scheme(scheme).encodedAuthority(authority).build().toString();
            }
        }
    } catch (Exception ignored) {
    }
    return url;
}

@Override
public void onClick(View v) {
    switch (v.getId()) {
        case R.id.btn_history:
            // 播放记录
            startActivity(new Intent(getActivity(), PlayRecordActivity.class));
            break;
        case R.id.btn_download:
            // 离线缓存
            //Toast.makeText(getContext(), "暂未开放此功能", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(getActivity(), ActvityDownVideo.class));
            break;
        case R.id.btn_star:
            // 我的追番
            startActivity(new Intent(getActivity(), MyStarActivity.class));
            break;
        case R.id.avatar_file:
            openImagePicker();
            break;
        case R.id.btn_settings:
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            break;
    }
}
@Override
public void onDetach() {
    super.onDetach();
    logoutListener = null; // 清理回调，防止内存泄漏
}

private static final int PICK_IMAGE_REQUEST = 1;
String fileExtension = "jpg"; // 默认扩展名
private void openImagePicker() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.setAction(Intent.ACTION_GET_CONTENT);  // 选择器
    startActivityForResult(Intent.createChooser(intent, "选择头像"), PICK_IMAGE_REQUEST);
}

@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK && data != null && data.getData() != null) {
        Uri imageUri = data.getData();
        uploadAvatar(imageUri); // 传入图片 URI 去上传
        Log.d("openImagePicker", "onActivityResult: "+imageUri);
    }
}
//修改或上传用户头像
private  void uploadAvatar(Uri imageUri){

    SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

    int userId=sharedPreferences.getInt("Id",-1);
    // 构建请求体
    if(userId==-1) {
        return;
    }
    byte[] bytes=null;
    try {
        InputStream inputStream = getContext().getContentResolver().openInputStream(imageUri);
        bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        inputStream.close();

        String mimeType = getContext().getContentResolver().getType(imageUri);
        Log.e("uploadAvatar", " uploadAvatar:mimeType " + mimeType);
        fileExtension = getFileExtensionFromMimeType(mimeType);
        if(fileExtension==null) return;
    } catch (FileNotFoundException e) {
        e.printStackTrace();
        // 可以在这里处理文件未找到的情况，例如提示用户重新选择文件
    } catch (IOException e) {
        e.printStackTrace();
        // 处理其他 I/O 异常
    }

// 获取扩展名后，构建完整的文件名
    String fileName = "avatar." + fileExtension;
    Log.e("uploadAvatar", " uploadAvatar: fileName" + fileName);
// 然后把 bytes 包装成 RequestBody 上传
    if(bytes!=null){

        // 1. 构建文件部分
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), bytes);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", fileName, requestFile);

        RequestBody urlFlagPart = RequestBody.create(MediaType.parse("text/plain"),String.valueOf(urlFlag));
        String token = AuthHeaderUtil.bearer(sharedPreferences.getString("token", ""));
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), "请先登录账户！", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonResModel> call = apiService.requestUploadAvatar(urlFlagPart, body, token);

        // 调用封装的 ApiClient 来处理请求
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                if (data.isSuccessCode()) {
                    Glide.with(getContext()).clear(ivAvatarFile);
                    if (data.getData() != null && !data.getData().isEmpty()) {
                        avatarRefresh(true, data.getData());
                    } else {
                        avatarRefresh(false, null);
                    }
                    Toast.makeText(getContext(), "修改头像成功！", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(getContext(), "修改头像失败！", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Login onSuccess: " + data.getMsg());
                }
               // Log.e(TAG, "Login onSuccess: " + data.getMsg());
            }

            @Override
            public void onFailure(String error) {

                Log.e(TAG, "Login failed: " + error);
                Toast.makeText(getContext(), "修改头像失败！", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
// 获取文件扩展名的方法
private String getFileExtensionFromMimeType(String mimeType) {
    if (mimeType == null) return "jpg"; // 默认返回 jpg

    if (mimeType.startsWith("image/")) {
        if (mimeType.equals("image/jpeg")) {
            return "jpg";
        } else if (mimeType.equals("image/png")) {
            return "png";
        } else if (mimeType.equals("image/gif")) {
            return "gif";
        } else if (mimeType.equals("image/webp")) {
            return "webp";
        }
    }
    return null; // 如果是其他类型，返回默认扩展名
}

}
