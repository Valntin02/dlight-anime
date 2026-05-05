package com.dlight.feature.user;

import java.io.Serializable;

public class UserResModel implements Serializable {
    private int code;
    private String username;
    private String avatarFile;
    private String msg;
    private String token;  // 增加token字段
    private int Id;
    // Getter 和 Setter 方法
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getId() {
        return Id;
    }

    public void setId(int Id) {
        this.Id = Id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarFile() {
        return avatarFile;
    }

    public void setAvatarFile(String avatarFile) {
        this.avatarFile = avatarFile;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getToken() {
        return token;  // 获取token
    }

    public void setToken(String token) {
        this.token = token;  // 设置token
    }

    @Override
    public String toString() {
        return "UserResModel{" +
            "code=" + code +
            "Id="+Id +'\'' +
            ", username='" + username + '\'' +
            ", avatarFile='" + avatarFile + '\'' +
            ", msg='" + msg + '\'' +
            ", token='" + token + '\'' +  // 输出token
            '}';
    }
}
