package com.dlight.feature.danmaku;

public class DanmakuReqModel {

    private Integer vodId; // 视频ID
    private Integer vodNid; // 第几集
    private float time; // 视频播放时间
    private int type; // 弹幕类型
    private int color; // 弹幕颜色
    private String content; // 弹幕内容
    private int userId; // 用户ID

    // 无参构造方法
    public DanmakuReqModel() {}

    // 全参构造方法
    public DanmakuReqModel(Integer vodId, Integer vodNid, float time, int type, int color, String content, int userId) {
        this.vodId = vodId;
        this.vodNid = vodNid;
        this.time = time;
        this.type = type;
        this.color = color;
        this.content = content;
        this.userId = userId;
    }

    // Getter 和 Setter 方法
    public Integer getVodId() {
        return vodId;
    }

    public void setVodId(Integer vodId) {
        this.vodId = vodId;
    }

    public Integer getVodNid() {
        return vodNid;
    }

    public void setVodNid(Integer vodNid) {
        this.vodNid = vodNid;
    }

    public float getTime() {
        return time;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    // toString 方法
    @Override
    public String toString() {
        return "DanmakuReqModel{" +
            "vodId=" + vodId +
            ", vodNid=" + vodNid +
            ", time=" + time +
            ", type=" + type +
            ", color=" + color +
            ", content='" + content + '\'' +
            ", userId=" + userId +
            '}';
    }




}

