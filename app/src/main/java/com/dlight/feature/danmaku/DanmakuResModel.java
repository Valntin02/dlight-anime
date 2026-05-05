package com.dlight.feature.danmaku;


import java.util.List;

public class DanmakuResModel {

    private int code;          // 状态码
    private List<DanmakuData> data;  // 弹幕数据

    // Getter 和 Setter
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<DanmakuData> getData() {
        return data;
    }

    public void setData(List<DanmakuData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "DanmakuResModel{" +
            "code=" + code +
            ", data=" + data +
            '}';
    }
}
