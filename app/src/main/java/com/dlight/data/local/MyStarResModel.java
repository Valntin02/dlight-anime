package com.dlight.data.local;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MyStarResModel {
    private int code;          // 状态码
    private List<MyStarRecord> data;
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

    public boolean isSuccessCode() {
        return code == 200 || code == 1;
    }
    public List<MyStarRecord> getData() {
        return data;
    }

    public void setData(List<MyStarRecord> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "MyStarResModel{" +
            "code=" + code +
            ", data=" + data +
            '}';
    }
}
