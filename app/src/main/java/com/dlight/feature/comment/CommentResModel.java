package com.dlight.feature.comment;

import com.dlight.feature.danmaku.DanmakuData;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CommentResModel {

    private int code;          // 状态码
    private List<CommentData> data;

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
    public List<CommentData> getData() {
        return data;
    }

    public void setData(List<CommentData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "CommentResModel{" +
            "code=" + code +
            ", data=" + data +
            '}';
    }
}
