package com.dlight.feature.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UserAvatarResModel {

    private int code;          // 状态码
    private List<UserAvatarData> data;
    @SerializedName(value = "msg", alternate = {"message"})
    private String msg;
    // Getter 和 Setter
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
    public List<UserAvatarData> getData() {
        return data;
    }

    public void setData(List<UserAvatarData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "UserAvatarResModel{" +
            "code=" + code +
            ", data=" + data +
            '}';
    }
}
