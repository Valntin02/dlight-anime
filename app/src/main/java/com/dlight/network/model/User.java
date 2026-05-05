package com.dlight.network.model;

import java.io.Serializable;

/**
 * 用户数据模型
 */
public class User implements Serializable {
    private int code;
    private String username;
    private String avatarFile;
    private String msg;
    private String token;
    private int id;

    public User() {}

    // Getter 和 Setter 方法
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public String Msg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "User{" +
                "code=" + code +
                ", id=" + id +
                ", username='" + username + '\'' +
                ", avatarFile='" + avatarFile + '\'' +
                ", msg='" + msg + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}
