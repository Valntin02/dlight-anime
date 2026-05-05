package com.dlight.data.model;


import java.util.Collection;
import java.util.List;

public class VodResModel {

    private int code;              // 状态码
    private List<VodData> data;        // 视频数据

    // 构造方法
    public VodResModel(int code, List<VodData> data) {
        this.code = code;
        this.data = data;
    }
    // 获取视频数据列表
    public List<VodData> getVodDataList() {
        return data;
    }
    // Getter 和 Setter
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<VodData> getData() {
        return data;
    }

    public void setData(List<VodData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "VodResModel{" +
            "code=" + code +
            ", data=" + data +
            '}';
    }


}
