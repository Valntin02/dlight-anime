package com.dlight.feature.comment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.ui.player.DanmkuVideoActivity;
import com.dlight.R;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;
import com.dlight.data.model.VodData;
import com.dlight.feature.user.UserAvatarData;
import com.dlight.feature.user.UserAvatarResModel;
import com.dlight.feature.user.UserResModel;
import com.dlight.util.Param;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;

public class CommentFragment extends Fragment implements CommentAdapter.OnReplyClickListener{
    private RecyclerView recyclerView;  // 使用 RecyclerView 替代 ExpandableListView
    private CommentAdapter commentAdapter;  // 保持适配器不变
    private List<CommentData> commentDataList = new ArrayList<>();
    private Map<Integer, String> map = new HashMap<>();
    private VodData vodData;

    private EditText commentEditText;
    private ImageView sendButton;

    private UserResModel userInfo;
    private CommentData currentReplyComment;  // 临时存储当前正在回复的评论

    public interface CommentCallback {
        void onSuccess();  // 评论成功的回调
        void onFailure(String error);  // 评论失败的回调
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment, container, false);


        // 获取 RecyclerView 和输入框视图
        View inputLayout = view.findViewById(R.id.comment_input_layout);

        if (inputLayout != null) {
            commentEditText = inputLayout.findViewById(R.id.comment_edit_text);
            sendButton = inputLayout.findViewById(R.id.send_button);
        }
        // 获取 activity 中的数据
        DanmkuVideoActivity activity = (DanmkuVideoActivity) getActivity();
        if (activity != null) {
            vodData = activity.getVideoData();
        } else {
            Log.e("CommentFragment", "get activity error");
        }

        // 向后端请求数据 异步请求
        getComment();  // 获取评论数据
        // 对评论进行处理分级

        // 使用 RecyclerView 替代 ExpandableListView
        recyclerView = view.findViewById(R.id.comment_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 设置适配器 这里设置的是旧引用，导致更新了也没有用
        commentAdapter = new CommentAdapter(getContext(), commentDataList, map,vodData.getVod_id());

        commentAdapter.setOnReplyClickListener(this);

        recyclerView.setAdapter(commentAdapter);

        userInfo=getUserInfo();

        sendButton.setOnClickListener(v -> {
            String replyText = commentEditText.getText().toString().trim();

            if (replyText.isEmpty()) {
                Toast.makeText(getContext(), "请输入回复内容！", Toast.LENGTH_SHORT).show();
                return;
            }
            if(userInfo == null){
                Toast.makeText(getContext(), "请先登录账户！", Toast.LENGTH_SHORT).show();
                return;
            }
            //当前评论不为空说明选定了评论
            if (currentReplyComment != null) {
                currentReplyComment.setCommentName(userInfo.getUsername());
                currentReplyComment.setUserId(userInfo.getId());
                currentReplyComment.setCommentContent(replyText);//设置评论内容
                currentReplyComment.setCommentPid(currentReplyComment.getCommentId());//子评论的父id设置
                String ip=Param.getInstance().getIpAddress();
                currentReplyComment.setIp(Param.ipToLong(ip));
                currentReplyComment.setCommentRid(vodData.getVod_id());
                currentReplyComment.setCommentTime((int) (System.currentTimeMillis() / 1000));

                replyComment(currentReplyComment, new CommentCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "发送评论成功！", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e("GetComment", "reply failure: " + error);
                        Toast.makeText(getContext(), "发送评论失败：" + error, Toast.LENGTH_SHORT).show();
                    }
                });
                commentEditText.setText("");
                commentEditText.setHint("发个友善的评论吧～");
                currentReplyComment = null;

            } else {
                //当前评论为空 没有选的评论回复，执行默认评论视频
                currentReplyComment=new CommentData(vodData.getVod_id(),userInfo.getId(),userInfo.getUsername());
                currentReplyComment.setCommentContent(replyText);
                String ip=Param.getInstance().getIpAddress();

                currentReplyComment.setIp(Param.ipToLong(ip));

                currentReplyComment.setCommentTime((int) (System.currentTimeMillis() / 1000));
                // 调用异步方法
                replyComment(currentReplyComment, new CommentCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getContext(), "发送评论成功！", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(String error) {
                        Log.e("GetComment", "reply failure: " + error);
                        Toast.makeText(getContext(), "发送评论失败：" + error, Toast.LENGTH_SHORT).show();
                    }
                });
                // 清空输入框
                commentEditText.setText("");
                commentEditText.setHint("发个友善的评论吧～");
                currentReplyComment=null;
            }
        });


        return view;
    }

    // 定义回调接口
    private void replyComment(CommentData commentData,CommentCallback callback) {

        String token = AuthHeaderUtil.bearer(userInfo.getToken());
        if (token.isEmpty()) {
            callback.onFailure("未登录或 token 无效");
            return;
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonResModel> call = apiService.requestReplyVod(commentData,token);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                Log.d("GetComment", "reply msg " + data);

                    if(data.isSuccessCode()){
                        callback.onSuccess();  // 成功后调用回调
                    }else{
                        String msg = data.getMsg() == null ? "reply fail" : data.getMsg();
                        callback.onFailure(msg);
                    }


            }
            @Override
            public void onFailure(String error) {
                Log.e("GetComment", "Error: " + error);

                    callback.onFailure(error);  // 失败后调用回调


            }
        });

    }


    @Override
    public void onReplyClick(CommentData comment){
        if (currentReplyComment != null) {
            currentReplyComment = null; // 清空当前回复的评论
        }
        currentReplyComment=new CommentData(comment);
        // 激活输入框并设置提示文本为回复的评论内容
        commentEditText.requestFocus();
        commentEditText.setHint("回复 " + comment.getCommentName() + ": " );  // 设置回复提示
        // 显示软键盘
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(commentEditText, InputMethodManager.SHOW_IMPLICIT);
        }

    }
    //获取用户头像
    private void fetchUserAvatar(Set<Integer> userIds){
        if (userIds.isEmpty()) return; // 防止空请求
        // 手动拼接字符串，适用于低版本 API（minSdkVersion 21+）
        StringBuilder userIdsString = new StringBuilder();
        for (Integer id : userIds) {
            if (userIdsString.length() > 0) {
                userIdsString.append(","); // 添加逗号分隔符
            }
            userIdsString.append(id);
        }

        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<UserAvatarResModel> call = apiService.requestUserAvatar(userIdsString.toString());
        Log.d("GetComment", "msg" + userIds);
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<UserAvatarResModel>() {
            @Override
            public void onSuccess(UserAvatarResModel data) {
                Log.d("GetComment", "msg" + data);


                if(data.getCode()==200){
                    List<UserAvatarData> temp=data.getData();
                    for (UserAvatarData t : temp) {
                        String userAvatar = t.getUserAvatar();
                        if (userAvatar != null && !userAvatar.startsWith("http")) {
                            userAvatar = Param.getInstance().getBaseUrl() + userAvatar;
                        }
                        Log.d("GetComment", "msg" + data);
                        map.put(t.getUserId(), userAvatar);
                    }
                    //commentAdapter.notifyDataSetChanged();
                    organizeComments(commentDataList);
                }
            }
            @Override
            public void onFailure(String error) {
                Log.e("GetComment", "Error: " + error);
            }
        });
    }
    private void organizeComments(List<CommentData> allComments) {
        Map<Integer, CommentData> commentMap = new HashMap<>();
        List<CommentData> topLevelComments = new ArrayList<>(); // 存储一级评论

        //  comment_id 存入 map,java里面 HashMap、ArrayList 等存储的都是对象的引用，而不是对象的副本。
        for (CommentData comment : allComments) {
            commentMap.put(comment.getCommentId(), comment);
        }

        // 第二步：根据 comment_pid 将回复添加到父评论的 replies 列表中
        for (CommentData comment : allComments) {
            if (comment.getCommentPid() == 0) {
                // 如果是一级评论，直接添加到 topLevelComments
                topLevelComments.add(comment);
            } else {
                // 如果是回复，找到对应的父评论，将其添加到父评论的 replies 列表中
                CommentData parentComment = commentMap.get(comment.getCommentPid());
                if (parentComment != null) {
                    parentComment.addReply(comment);

                    Log.d("organizeComments", comment.toString());
                }
            }
        }

        // 将一级评论传递给 adapter，适配器展示一级评论及其回复
        //改变了引用
        //commentDataList = topLevelComments;
        // 清空原列表，但引用地址不变
        commentDataList.clear();
        // 追加新的分级数据
        commentDataList.addAll(topLevelComments);

        commentAdapter.notifyDataSetChanged();
    }
    private void getComment() {


        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<CommentResModel> call = apiService.requestComment(vodData.getVod_id());
        Log.d("GetComment", "msg" + vodData.getVod_id());
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<CommentResModel>() {
            @Override
            public void onSuccess(CommentResModel data) {
                Log.d("GetComment", "msg" + data);
                if(data.getCode()==200){
                    commentDataList.addAll(data.getData());
                    // 收集所有 user_id
                    Set<Integer> userIds = new HashSet<>();
                    for (CommentData comment : commentDataList) {
                        userIds.add(comment.getUserId());
                    }
//                    userIds.add(2);
//                    userIds.add(3);
                    fetchUserAvatar(userIds);
                }
            }
            @Override
            public void onFailure(String error) {
                Log.e("GetComment", "Error: " + error);
            }
        });
    }

    // 从 SharedPreferences 中读取用户信息
    public UserResModel getUserInfo() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // 检查是否已经登录
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
        if (!isLoggedIn) {
            return null;  // 用户未登录，返回 null
        }

        // 将数据封装到 UserResModel 中
        UserResModel user = new UserResModel();
        String token = sharedPreferences.getString("token", "");
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        user.setToken(token);
        user.setUsername(sharedPreferences.getString("username", ""));
        user.setAvatarFile(sharedPreferences.getString("avatarFile", ""));
        user.setId(sharedPreferences.getInt("Id", 0));
        return user;
    }
}
