package com.dlight.feature.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;


import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import retrofit2.Call;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class LoginFragment extends Fragment {
    private EditText etUsername, etPassword;
    private Button btnLogin,btnRegister;
    private static final String TAG = "LoginFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 返回你的布局文件
        View rootView = inflater.inflate(R.layout.fragment_login, container, false);

        // 初始化视图
        etUsername = rootView.findViewById(R.id.et_username);
        etPassword = rootView.findViewById(R.id.et_password);
        btnLogin = rootView.findViewById(R.id.btn_login);
        btnRegister = rootView.findViewById(R.id.btn_register);

        // 设置登录按钮点击事件
        btnLogin.setOnClickListener(v -> login());

        btnRegister.setOnClickListener(v->register());

        return rootView;
    }


    private void login(){
        String username = etUsername.getText().toString();
        String password = etPassword.getText().toString();

        if(username.isEmpty() || password.isEmpty() || password.length()<6){
            Toast.makeText(getContext(), "请输入正确的用户或密码！", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建 Retrofit 实例
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<UserResModel> call = apiService.login(username, password);

        // 调用封装的 ApiClient 来处理请求
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<UserResModel>() {
            @Override
            public void onSuccess(UserResModel data) {
                if (data.getCode() == 200) {
                    Log.d(TAG, "Login successful: " + data.getToken());
                    // 登录成功后，保存用户信息和登录状态
                    saveLoginData(data);
                    // 登录成功后，将数据传递给 UserFragment
//                        Bundle bundle = new Bundle();
//                        bundle.putSerializable("user_data", data);  // 将 UserResModel 对象放入 Bundle

                    // 创建 UserFragment 实例并设置数据
                    UserFragment userFragment = new UserFragment();
//                        userFragment.setArguments(bundle);  // 设置传递的参数

                    // 替换当前的 Fragment
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_login, userFragment); // 使用合适的容器ID
//                        transaction.addToBackStack(null); // 可选，允许返回操作
                    transaction.commit();
                } else {
                    Toast.makeText(getContext(), "登录失败！", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(String error) {

                Log.e(TAG, "Login failed: " + error);
                Toast.makeText(getContext(), "登录失败！", Toast.LENGTH_SHORT).show();
            }
        });

    }

    //目前这里临时设置一个注册 后期可能考虑短信或者手机号验证单独创建页面
    private void register(){

        String username = etUsername.getText().toString();
        String password = etPassword.getText().toString();

        if(username.isEmpty() || password.isEmpty() || password.length()<6){
            Toast.makeText(getContext(), "请输入正确的用户或密码！", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建 Retrofit 实例
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<UserResModel> call = apiService.register(username, password);

        // 调用封装的 ApiClient 来处理请求
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<UserResModel>() {
            @Override
            public void onSuccess(UserResModel data) {
                if (data.getCode() == 200) {
                    Log.d(TAG, "Login successful: " + data.getToken());
                    Toast.makeText(getContext(), "注册成功！", Toast.LENGTH_SHORT).show();
                } if (data.getCode() == 0) {
                    Toast.makeText(getContext(), "用户名已被注册！", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(getContext(), "注册失败！", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Login failed: " + error);
                Toast.makeText(getContext(), "注册失败！", Toast.LENGTH_SHORT).show();
            }
        });
    }
    // 保存登录状态和用户信息到 SharedPreferences
    private void saveLoginData(UserResModel data) {
        //SharedPreferences 是通过文件来存储数据的，每次调用 getSharedPreferences() 时，系统会返回相同的文件。
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("token", data.getToken());  // 保存 token
        editor.putBoolean("is_logged_in", true);  // 设置登录状态为 true
        editor.putString("username", data.getUsername());  // 保存用户名
        editor.putString("avatarFile", data.getAvatarFile());  // 保存头像文件
        editor.putInt("Id",data.getId());
        editor.apply();  // 保存数据
    }
}
