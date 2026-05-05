package com.dlight.feature.comment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dlight.R;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.util.Param;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;
import com.dlight.util.LikeCacheManager;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private final List<CommentData> commentList;  // 评论数据列表
    private final Map<Integer, String> userAvatarMap;  // 用户头像映射
    private final Context context;
    private  int vodId;//表示当前页面视频的id;
    private OnReplyClickListener onReplyClickListener;  // 添加回调接口

    // 定义一个接口，当点击回复按钮时通知 Fragment
    public interface OnReplyClickListener {
        void onReplyClick(CommentData comment);  // 传递当前评论对象
    }
    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.onReplyClickListener = listener;
    }
    public CommentAdapter(Context context, List<CommentData> commentList, Map<Integer, String> userAvatarMap,int vodId) {
        this.context = context;
        this.commentList = commentList;
        this.userAvatarMap = userAvatarMap;
        this.vodId=vodId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.comment_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommentData comment = commentList.get(position);

        holder.userName.setText(comment.getCommentName());

        // 更改点赞按钮图片
        // 缓存状态
       int flag=LikeCacheManager.getInstance(context).getLikeCount(comment.getCommentId());
        if(flag==0){
            String numb=String.valueOf(comment.getCommentUp());
            holder.commentUp.setText(numb);
        }else{
            String numb=String.valueOf(flag);
            holder.commentUp.setText(numb);
        }

        if ( LikeCacheManager.getInstance(context).isLiked(comment.getCommentId())) {
            holder.likeButton.setImageResource(R.drawable.material_thumbsup_liked);  // 替换为点赞图片
        } else {
            holder.likeButton.setImageResource(R.drawable.material_thumbsup);  // 替换为未点赞图片
        }
        // 转换为毫秒级时间戳
        long timestamp = comment.getCommentTime() * 1000L;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        Date date = new Date(timestamp);
        holder.postTime.setText(sdf.format(date));

        holder.commentContent.setText(comment.getCommentContent());
        int commentCount = comment.getRepliesList() != null ? comment.getRepliesList().size() : 0;
       //这里存在一个复用问题 如果等于0 由于复用holder的关系 导致 当前的commentDetail没有设置为Gone 从而显示了不该有的显示
        if(commentCount>0){
            String commentTitle = String.format("共有 %d 条回复", commentCount);
            holder.commentDetail.setText(commentTitle);
            holder.commentDetail.setVisibility(View.VISIBLE);
            Log.d("commentcount", "onBindViewHolder: "+commentCount);
        }else{
            holder.commentDetail.setVisibility(View.GONE);
        }

        if(comment.isReported()){
            holder.reportButton.setImageResource(R.drawable.material_reported);
        }else{
            holder.reportButton.setImageResource(R.drawable.material_report);
        }
        // 使用 Glide 加载头像
        Glide.with(context)
            .load(userAvatarMap.get(comment.getUserId()))
            .circleCrop()
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.button_style)
            .into(holder.userAvatar);

        //评论详情查看
        holder.commentDetail.setOnClickListener(v->{
            FragmentActivity activity = (FragmentActivity) v.getContext();
            //DialogFragment BottomSheetFragment 和 ViewPager 之间并不会直接产生冲突，主要是因为它们在界面中承担了不同的职
            //Dialog 是一个弹出的对话框，通常使用 setContentView() 来设置布局，而不能直接通过 Fragment 的 onCreateView() 来绑定布局。
            //允许你像普通 Fragment 一样在 onCreateView() 中绑定布局，并且能够提供一个底部滑出的效果。
            //一开始使用dialog但是使用不了绑定的布局，搞了一个半小时也没有得到满意的效果,最后使用bottomsheet一下就解决了
            // 创建 BottomSheetDialogFragment 并传入评论 ID
            CommentBottomSheetFragment fragment =new CommentBottomSheetFragment(comment,comment.getRepliesList(),userAvatarMap,vodId);
            fragment.show(activity.getSupportFragmentManager(), "comment_sheet");
        });
//
//        // 恢复缓存点赞状态
//        holder.likeButton.setSelected(LikeCacheManager.getInstance(context).isLiked(comment.getCommentId()));
        //之前调试的时候这里忘记关闭了，导致一开始没有缓存文件然后全部显示为0
//        holder.commentUp.setText(String.valueOf(LikeCacheManager.getInstance(context).getLikeCount(comment.getCommentId())));

        // 点赞按钮
        holder.likeButton.setOnClickListener(v -> {
            // 处理点赞逻辑
            if (comment.isPendingLike()) {
                // 如果正在进行点赞请求，则忽略点击
                return;
            }

            SharedPreferences sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            // 检查是否已经登录
            boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
            if (!isLoggedIn) {
                Toast.makeText(context, "请先登录账户！", Toast.LENGTH_SHORT).show();
                return;  // 用户未登录，返回 null
            }
            String token = AuthHeaderUtil.bearer(sharedPreferences.getString("token", ""));
            int useId=sharedPreferences.getInt("Id", 0);
            int olduseId=comment.getUserId(); //需要记录 这里利用这个字段修改，如果不恢复导致下次操作可能失败
            comment.setUserId(useId); //这里设定为点赞人Id，直接使用comment的user_id结构，简化数据
            // 本地立即变更 UI 状态
            comment.setPendingLike(true);
            comment.setLikeCache(comment.isLiked());  // 缓存当前状态
            comment.setLiked(!comment.isLiked());     // 切换点赞状态
            comment.setCommentUp(comment.isLiked() ? comment.getCommentUp() + 1 : comment.getCommentUp() - 1);

            // 更改点赞按钮图片
            if (comment.isLiked()) {
                holder.likeButton.setImageResource(R.drawable.material_thumbsup_liked);  // 替换为点赞图片
            } else {
                holder.likeButton.setImageResource(R.drawable.material_thumbsup);  // 替换为未点赞图片
            }

            // 缓存状态
            LikeCacheManager.getInstance(context)
                .cacheLikeStatus(comment.getCommentId(), comment.isLiked(), comment.getCommentUp());

            notifyItemChanged(position);

            // 防抖延迟 3 秒后同步网络
            LikeCacheManager.getInstance(context).delayNetworkSync(comment.getCommentId(), () -> {
                CommentLikeRequest(comment, token, holder,comment.isLiked(),olduseId);
            }, 2000);
        });

        // 回复按钮
        holder.replyButton.setOnClickListener(v -> {
            if (onReplyClickListener != null) {
                onReplyClickListener.onReplyClick(comment);  // 点击时回调
            }
        });

        // 举报按钮
        holder.reportButton.setOnClickListener(v -> {
            // 处理举报逻辑
            SharedPreferences sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            // 检查是否已经登录
            boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
            if (!isLoggedIn) {
                Toast.makeText(context, "请先登录账户！", Toast.LENGTH_SHORT).show();
                return;  // 用户未登录，返回 null
            }
            holder.reportButton.setImageResource(R.drawable.material_reported);
            String token = AuthHeaderUtil.bearer(sharedPreferences.getString("token", ""));
            int useId=sharedPreferences.getInt("Id", 0);
            if(useId==0) return;
            int oldUseId=comment.getUserId();
            comment.setUserId(useId); //这里设定为举报人Id，直接使用comment的user_id结构，简化数据

            //举报的请求
            CommentReportRequest(comment, token,oldUseId,holder);

        });
    }
    //点赞请求
    private void CommentLikeRequest(CommentData comment, String token, ViewHolder holder,boolean isLike,int oldUseId){
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);

            Call<JsonResModel> call = apiService.requestCommentUpDown(comment.getCommentId(), comment.isLiked(), token);
            ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
                @Override
                public void onSuccess(JsonResModel data) {
                    Log.d("GetComment", "msg" + data);
                    if(isLike){
                        if(data.getCode()==0){
                            Toast.makeText(context, "你已经点过赞啦！", Toast.LENGTH_SHORT).show();
                        }else if (data.getCode() == 200){
                            Toast.makeText(context, "点赞成功！", Toast.LENGTH_SHORT).show();
                        }else{
                            // 恢复点赞状态，防止数据不一致
                            comment.setLiked(comment.getLikeCache());
                            comment.setCommentUp(comment.isLiked() ? comment.getCommentUp() + 1 : comment.getCommentUp() - 1);
                            // 恢复缓存中的状态
                            LikeCacheManager.getInstance(context)
                                .cacheLikeStatus(comment.getCommentId(), comment.getLikeCache(), comment.getCommentUp());

                            notifyItemChanged(holder.getAdapterPosition());
                            Toast.makeText(context, "点赞失败！", Toast.LENGTH_SHORT).show();
                        }
                    }else{
                        if (data.getCode() == 200){
                            Toast.makeText(context, "取消点赞成功！", Toast.LENGTH_SHORT).show();
                        }else{
                            // 恢复点赞状态，防止数据不一致
                            comment.setLiked(comment.getLikeCache());
                            comment.setCommentUp(comment.isLiked() ? comment.getCommentUp() + 1 : comment.getCommentUp() - 1);
                            // 恢复缓存中的状态
                            LikeCacheManager.getInstance(context)
                                .cacheLikeStatus(comment.getCommentId(), comment.getLikeCache(), comment.getCommentUp());


                            notifyItemChanged(holder.getAdapterPosition());
                            Toast.makeText(context, "取消点赞失败！", Toast.LENGTH_SHORT).show();
                        }
                    }
                    comment.setPendingLike(false);  // 恢复可点击状态
                    comment.setUserId(oldUseId);
                }
                @Override
                public void onFailure(String error) {
                    Log.e("GetComment", "Error: " + error);

                    // 恢复点赞状态，防止数据不一致
                    comment.setLiked(comment.getLikeCache());
                    comment.setCommentUp(comment.isLiked() ? comment.getCommentUp() + 1 : comment.getCommentUp() - 1);
                    // 恢复缓存中的状态
                    LikeCacheManager.getInstance(context)
                        .cacheLikeStatus(comment.getCommentId(), comment.getLikeCache(), comment.getCommentUp());

                    comment.setPendingLike(false);  // 恢复可点击状态
                    notifyItemChanged(holder.getAdapterPosition());
                    if(isLike){
                        Toast.makeText(context, "点赞失败！", Toast.LENGTH_SHORT).show();
                    }else{

                        Toast.makeText(context, "取消点赞失败！", Toast.LENGTH_SHORT).show();
                    }
                    comment.setUserId(oldUseId);
                }
            });


    }


    //举报评论请求
    private void CommentReportRequest(CommentData comment, String token,int oldUseId, ViewHolder holder){
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);

        Call<JsonResModel> call = apiService.requestCommentReport(comment.getCommentId(), token);
        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                Log.d("reportComment", "msg" + data);

                    if(data.getCode()==0){
                        Toast.makeText(context, "你已经举报过该评论！", Toast.LENGTH_SHORT).show();
                    }else if(data.getCode()==200){
                        Toast.makeText(context, "举报成功！", Toast.LENGTH_SHORT).show();
                    }else{
                        //不能在函数里面传入holder 当 RecyclerView 进行滑动复用 导致修改到其他的item上面 造成bug
                       //holder.reportButton.setImageResource(R.drawable.material_report);

                        //通知UI改变 会去调用 onBindViewHolder
                        comment.setReported(false);
                        notifyItemChanged(holder.getAdapterPosition());
                         Toast.makeText(context, "举报失败！", Toast.LENGTH_SHORT).show();
                    }
                comment.setUserId(oldUseId);
            }
            @Override
            public void onFailure(String error) {
                Log.e("reportComment", "Error: " + error);
                comment.setReported(false);
                notifyItemChanged(holder.getAdapterPosition());
                Toast.makeText(context, "举报失败！", Toast.LENGTH_SHORT).show();
                comment.setUserId(oldUseId);
                }

        });

    }

    @Override
    public int getItemCount() {
        return commentList != null ? commentList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar, likeButton, replyButton, reportButton;
        TextView userName, postTime, commentContent,commentDetail,commentUp;
        // 获取包含输入框的布局

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.user_avatar);
            userName = itemView.findViewById(R.id.user_name);
            postTime = itemView.findViewById(R.id.post_time);
            commentContent = itemView.findViewById(R.id.comment_content);
            likeButton = itemView.findViewById(R.id.like_button);
            replyButton = itemView.findViewById(R.id.reply_button);
            reportButton = itemView.findViewById(R.id.report_button);
            commentDetail= itemView.findViewById(R.id.comment_detail);
            commentUp= itemView.findViewById(R.id.comment_ups);
        }
    }
}
