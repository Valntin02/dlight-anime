package com.dlight.feature.comment;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import com.dlight.network.model.Comment;

public class CommentData {

    private int commentId;        // 评论ID
    private int commentRid;       // 评论的关联ID（如回复的评论ID）
    private int commentPid;       // 评论的父级ID（通常是某个帖子或视频的ID）
    private int userId;           // 用户ID
    private String commentName;   // 用户名称
    private int commentTime; // 评论时间 数据库保存的是int类型，如果定义为时间戳类型会报错，这里客户端拿到int类再转换
    private String commentContent; // 评论内容
    private int commentUp;        // 点赞数量
    private int commentReply;     // 回复数
    private int commentReport;    // 举报数

    private int commentAtUser; //表示当前评论回复的对象

    private boolean isLiked;     // 本地点赞状态
    private boolean pendingLike; // 是否正在发送请求
    private boolean likeCache;   // 缓存上次的点赞状态

    private boolean isReported;

    public void setCommentAtUser(int commentAtUser){
        this.commentAtUser=commentAtUser;
    }
    public int getCommentAtUser(){
        return this.commentAtUser;
    }
    public boolean isReported() {
        return isReported;
    }

    public void setReported(boolean isReported) {
        this.isReported = isReported;
    }

    public boolean isLiked() {
        return isLiked;
    }

    public void setLiked(boolean liked) {
        this.isLiked = liked;
    }

    public boolean isPendingLike() {
        return pendingLike;
    }

    public void setPendingLike(boolean pendingLike) {
        this.pendingLike = pendingLike;
    }

    public boolean getLikeCache() {
        return likeCache;
    }

    public void setLikeCache(boolean likeCache) {
        this.likeCache = likeCache;
    }
    private long Ip;
    private List<CommentData> replies=new ArrayList<>();; // 存储回复
    // 添加回复的方法
    public void addReply(CommentData reply) {
        replies.add(reply);
    }

    public List<CommentData> getRepliesList(){
        return this.replies;
    }
    // 默认构造函数
    public CommentData() {}

    // 带参数的构造函数
    public CommentData(int commentId, int commentRid, int commentPid, int userId, String commentName, int commentTime,
                   String commentContent, int commentUp, int commentReply, int commentReport,int commentAtUser) {
        this.commentId = commentId;
        this.commentRid = commentRid;
        this.commentPid = commentPid;
        this.userId = userId;
        this.commentName = commentName;
        this.commentTime = commentTime;
        this.commentContent = commentContent;
        this.commentUp = commentUp;
        this.commentReply = commentReply;
        this.commentReport = commentReport;
        this.commentAtUser=commentAtUser;
    }

    // 重载构造函数，只传入部分参数
    public CommentData(int commentRid, int userId, String commentName) {
        this.commentRid = commentRid;
        this.userId = userId;
        this.commentName = commentName;

        // 设置其他属性默认值
        this.commentId = 0;                  // 默认为 0
        this.commentPid = 0;                  // 默认为 0
        this.commentTime = (int) (System.currentTimeMillis() / 1000);  // 当前时间戳
        this.commentContent = "";             // 默认为空字符串
        this.commentUp = 0;                   // 默认为 0
        this.commentReply = 0;                // 默认为 0
        this.commentReport = 0;               // 默认为 0
    }

    // 复制构造函数
    public CommentData(CommentData other) {
        this.commentId = other.commentId;
        this.commentRid = other.commentRid;
        this.commentPid = other.commentPid;
        this.userId = other.userId;
        this.commentName = other.commentName;
        this.commentTime = other.commentTime;
        this.commentContent = other.commentContent;
        this.commentUp = other.commentUp;
        this.commentReply = other.commentReply;
        this.commentReport = other.commentReport;
        this.replies=null;
    }
    // Getter and Setter 方法
    public int getCommentId() {
        return commentId;
    }

    public void setCommentId(int commentId) {
        this.commentId = commentId;
    }

    public int getCommentRid() {
        return commentRid;
    }

    public void setIp(long Ip) {
        this.Ip = Ip;
    }

    public long getIp() {
        return Ip;
    }
    public void setCommentRid(int commentRid) {
        this.commentRid = commentRid;
    }

    public int getCommentPid() {
        return commentPid;
    }

    public void setCommentPid(int commentPid) {
        this.commentPid = commentPid;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCommentName() {
        return commentName;
    }

    public void setCommentName(String commentName) {
        this.commentName = commentName;
    }

    public int getCommentTime() {
        return commentTime;
    }

    public void setCommentTime(int commentTime) {
        this.commentTime = commentTime;
    }

    public String getCommentContent() {
        return commentContent;
    }

    public void setCommentContent(String commentContent) {
        this.commentContent = commentContent;
    }

    public int getCommentUp() {
        return commentUp;
    }

    public void setCommentUp(int commentUp) {
        this.commentUp = commentUp;
    }

    public int getCommentReply() {
        return commentReply;
    }

    public void setCommentReply(int commentReply) {
        this.commentReply = commentReply;
    }

    public int getCommentReport() {
        return commentReport;
    }

    public void setCommentReport(int commentReport) {
        this.commentReport = commentReport;
    }

    // toString 方法，方便打印
    @Override
    public String toString() {
        return "Comment{" +
            "commentId=" + commentId +
            ", commentRid=" + commentRid +
            ", commentPid=" + commentPid +
            ", userId=" + userId +
            ", commentName='" + commentName + '\'' +
            ", commentTime=" + commentTime +
            ", commentContent='" + commentContent + '\'' +
            ", commentUp=" + commentUp +
            ", commentReply=" + commentReply +
            ", commentReport=" + commentReport +
            '}';
    }
}
