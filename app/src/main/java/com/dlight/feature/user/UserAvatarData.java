package com.dlight.feature.user;

public class UserAvatarData {
    private int userId;
    private String userAvatar;

    // 构造函数
    public UserAvatarData(int userId, String userAvatar) {
        this.userId = userId;
        this.userAvatar = userAvatar;
    }

    // Getter 和 Setter 方法
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    @Override
    public String toString() {
        return "UserAvatarData{" +
            "userId=" + userId +
            ", userAvatar=" + userAvatar +
            '}';
    }
}

