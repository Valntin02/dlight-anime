package com.dlight.network.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论数据模型
 */
public class Comment {

    private int commentId;          // 评论ID
    private int commentRid;         // 评论的关联ID（如回复的评论ID）
    private int commentPid;         // 评论的父级ID（通常是某个帖子或视频的ID）
    private int userId;             // 用户ID
    private String commentName;     // 用户名称
    private int commentTime;        // 评论时间
    private String commentContent; // 评论内容
    private int commentUp;         // 点赞数量
    private int commentReply;       // 回复数
    private int commentReport;      // 举报数
    private int commentAtUser;      // 表示当前评论回复的对象
    private long ip;

    private boolean isLiked;        // 本地点赞状态
    private boolean pendingLike;   // 是否正在发送请求
    private boolean likeCache;     // 缓存上次的点赞状态
    private boolean isReported;

    private List<Comment> replies = new ArrayList<>(); // 存储回复

    public Comment() {}

    // 带参数的构造函数
    public Comment(int commentId, int commentRid, int commentPid, int userId, String commentName,
                   int commentTime, String commentContent, int commentUp, int commentReply,
                   int commentReport, int commentAtUser) {
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
        this.commentAtUser = commentAtUser;
    }

    // 重载构造函数，只传入部分参数
    public Comment(int commentRid, int userId, String commentName) {
        this.commentRid = commentRid;
        this.userId = userId;
        this.commentName = commentName;
        this.commentTime = (int) (System.currentTimeMillis() / 1000);
    }

    // 复制构造函数
    public Comment(Comment other) {
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
        this.replies = null;
    }

    // 添加回复的方法
    public void addReply(Comment reply) {
        replies.add(reply);
    }

    public List<Comment> getRepliesList() {
        return this.replies;
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

    public void setCommentAtUser(int commentAtUser) {
        this.commentAtUser = commentAtUser;
    }

    public int getCommentAtUser() {
        return this.commentAtUser;
    }

    public boolean isLiked() {
        return isLiked;
    }

    public void setLiked(boolean liked) {
        isLiked = liked;
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

    public boolean isReported() {
        return isReported;
    }

    public void setReported(boolean reported) {
        isReported = reported;
    }

    public void setIp(long ip) {
        this.ip = ip;
    }

    public long getIp() {
        return ip;
    }

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
