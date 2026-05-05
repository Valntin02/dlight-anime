package com.dlight.feature.danmaku;

public class DanmakuData {

    private String text;  // 弹幕内容
    private int color;    // 弹幕颜色
    private float time;   // 弹幕出现时间，改为 float 类型
    private int type;     // 弹幕类型

    // Getter 和 Setter
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
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

    @Override
    public String toString() {
        return "DanmakuData{" +
            "text='" + text + '\'' +
            ", color=" + color +
            ", time=" + time +
            ", type=" + type +
            '}';
    }
}
