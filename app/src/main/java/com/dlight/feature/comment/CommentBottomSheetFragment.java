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
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dlight.R;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.model.VodData;
import com.dlight.feature.user.UserResModel;
import com.dlight.util.Param;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

public class CommentBottomSheetFragment extends BottomSheetDialogFragment  implements CommentAdapter.OnReplyClickListener{

    private RecyclerView recyclerView;
    private CommentReplyAdapter commentAdapter;
    private List<CommentData> subComments = new ArrayList<>();

    private UserResModel userInfo;
    private ImageView sendButton;
    private EditText commentEditText;
    private CommentData currentReplyComment;  // 临时存储当前正在回复的评论
    private Map<Integer, String> userAvatarMap=new HashMap<>();

    private CommentData commentData; //表示当前页面的父评论

    private int vodId;//表示当前视频的id
    public CommentBottomSheetFragment(CommentData commentData, List<CommentData> subComments,Map<Integer, String> map,int vodId){
            this.commentData=commentData;
            this.subComments=subComments;
            this.userAvatarMap=map;
            this.vodId=vodId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment_bottom_sheet, container, false);

        View inputLayout = view.findViewById(R.id.comment_input_layout);

        if (inputLayout != null) {
            commentEditText = inputLayout.findViewById(R.id.comment_edit_text);
            sendButton = inputLayout.findViewById(R.id.send_button);
        }
        // 使用 RecyclerView 替代 ExpandableListView
        recyclerView = view.findViewById(R.id.comment_sub_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 设置适配器
        commentAdapter = new CommentReplyAdapter(getContext(), subComments, userAvatarMap);
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
            //需要添加一个@的功能
            if (currentReplyComment != null) {
                currentReplyComment.setCommentRid(vodId);
                currentReplyComment.setCommentName(userInfo.getUsername());
                currentReplyComment.setUserId(userInfo.getId());
                currentReplyComment.setCommentContent(replyText);//设置评论内容
                currentReplyComment.setCommentPid(currentReplyComment.getCommentId());//子评论的父id设置
                String ip= Param.getInstance().getIpAddress();
                currentReplyComment.setIp(Param.ipToLong(ip));
                currentReplyComment.setCommentTime((int) (System.currentTimeMillis() / 1000));
                //设置此评论回复的对象
                currentReplyComment.setCommentAtUser(currentReplyComment.getUserId());

                replyComment(currentReplyComment, new CommentFragment.CommentCallback() {
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
                //当前评论为空 没有选的评论回复，执行默认回复父评论
                currentReplyComment=new CommentData(vodId,userInfo.getId(),userInfo.getUsername());
               //设置设置父评论 这个和第一次fragment逻辑有点不同
                currentReplyComment.setCommentPid(commentData.getCommentId());
                currentReplyComment.setCommentContent(replyText);
                String ip=Param.getInstance().getIpAddress();
                currentReplyComment.setIp(Param.ipToLong(ip));
                currentReplyComment.setCommentTime((int) (System.currentTimeMillis() / 1000));
                // 调用异步方法
                replyComment(currentReplyComment, new CommentFragment.CommentCallback() {
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

    private void replyComment(CommentData commentData, CommentFragment.CommentCallback callback) {

        String token = AuthHeaderUtil.bearer(userInfo.getToken());
        if (token.isEmpty()) {
            if (callback != null) {
                callback.onFailure("未登录或 token 无效");
            }
            return;
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonResModel> call = apiService.requestReplyVod(commentData,token);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                Log.d("GetComment", "reply msg " + data);
                if (callback == null) {
                    return;
                }
                if (data != null && data.isSuccessCode()) {
                    callback.onSuccess();
                } else {
                    String msg = data == null ? "reply fail" : data.getMsg();
                    callback.onFailure(msg == null ? "reply fail" : msg);
                }
            }
            @Override
            public void onFailure(String error) {
                Log.e("GetComment", "Error: " + error);
                if (callback != null) {
                    callback.onFailure(error);  // 失败后调用回调
                }

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
