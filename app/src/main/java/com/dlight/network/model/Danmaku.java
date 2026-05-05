package com.dlight.network.model;

/**
 * 弹幕数据模型
 */
public class Danmaku {

    private String text;   // 弹幕内容
    private int color;      // 弹幕颜色
    private float time;     // 弹幕出现时间
    private int type;       // 弹幕类型

    public Danmaku() {}

    public Danmaku(String text, int color, float time, int type) {
        this.text = text;
        this.color = color;
        this.time = time;
        this.type = type;
    }

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
        return "Danmaku{" +
                "text='" + text + '\'' +
                ", color=" + color +
                ", time=" + time +
                ", type=" + type +
                '}';
    }
}
