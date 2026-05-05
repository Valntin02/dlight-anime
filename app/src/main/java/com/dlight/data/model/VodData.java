package com.dlight.data.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

public class VodData implements Parcelable {

    protected int vod_id;
    protected String vod_name;
    protected String vod_pic;
    protected String vod_play_url;
    @SerializedName("vod_play_data")
    protected JsonElement vodPlayData;
    protected String vod_actor;
    protected String vod_remarks;  //目前更新到多少级
    protected String vod_year;
    protected String vod_content;
    protected String vod_total; //总集

    // 构造方法
    public VodData(int vod_id, String vod_name, String vod_pic, String vod_play_url,
                   String vod_actor, String vod_remarks, String vod_year, String vod_content, String vod_total) {
        this.vod_id = vod_id;
        this.vod_name = vod_name;
        this.vod_pic = vod_pic;
        this.vod_play_url = vod_play_url;
        this.vod_actor = vod_actor;
        this.vod_remarks = vod_remarks;
        this.vod_year = vod_year;
        this.vod_content = vod_content;
        this.vod_total = vod_total;
    }

    // Parcelable 构造方法
    protected VodData(Parcel in) {
        vod_id = in.readInt();
        vod_name = in.readString();
        vod_pic = in.readString();
        vod_play_url = in.readString();
        String vodPlayDataJson = in.readString();
        if (vodPlayDataJson != null && !vodPlayDataJson.isEmpty()) {
            try {
                vodPlayData = new JsonParser().parse(vodPlayDataJson);
            } catch (Exception ignored) {
                vodPlayData = null;
            }
        }
        vod_actor = in.readString();
        vod_remarks = in.readString();
        vod_year = in.readString();
        vod_content = in.readString();
        vod_total = in.readString();
    }

    // 实现 Parcelable 接口的方法
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(vod_id);
        dest.writeString(vod_name);
        dest.writeString(vod_pic);
        dest.writeString(vod_play_url);
        dest.writeString(vodPlayData == null ? null : vodPlayData.toString());
        dest.writeString(vod_actor);
        dest.writeString(vod_remarks);
        dest.writeString(vod_year);
        dest.writeString(vod_content);
        dest.writeString(vod_total);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VodData> CREATOR = new Creator<VodData>() {
        @Override
        public VodData createFromParcel(Parcel in) {
            return new VodData(in);
        }

        @Override
        public VodData[] newArray(int size) {
            return new VodData[size];
        }
    };

    // Getter 和 Setter 方法
    public int getVod_id() {
        return vod_id;
    }

    public void setVod_id(int vod_id) {
        this.vod_id = vod_id;
    }

    public String getVod_name() {
        return vod_name;
    }

    public void setVod_name(String vod_name) {
        this.vod_name = vod_name;
    }

    public String getVod_pic() {
        return vod_pic;
    }

    public void setVod_pic(String vod_pic) {
        this.vod_pic = vod_pic;
    }

    public String getVod_play_url() {
        return vod_play_url;
    }

    public void setVod_play_url(String vod_play_url) {
        this.vod_play_url = vod_play_url;
    }

    public JsonElement getVodPlayData() {
        return vodPlayData;
    }

    public void setVodPlayData(JsonElement vodPlayData) {
        this.vodPlayData = vodPlayData;
    }

    public String getVod_actor() {
        return vod_actor;
    }

    public void setVod_actor(String vod_actor) {
        this.vod_actor = vod_actor;
    }

    public String getVod_remarks() {
        return vod_remarks;
    }

    public void setVod_remarks(String vod_remarks) {
        this.vod_remarks = vod_remarks;
    }

    public String getVod_year() {
        return vod_year;
    }

    public void setVod_year(String vod_year) {
        this.vod_year = vod_year;
    }

    public String getVod_content() {
        return vod_content;
    }

    public void setVod_content(String vod_content) {
        this.vod_content = vod_content;
    }

    public String getVod_total() {
        return vod_total;
    }

    public void setVod_total(String vod_total) {
        this.vod_total = vod_total;
    }

    @Override
    public String toString() {
        return "VodData{" +
            "vod_id=" + vod_id +
            ", vod_name='" + vod_name + '\'' +
            ", vod_pic='" + vod_pic + '\'' +
            ", vod_play_url='" + vod_play_url + '\'' +
            ", vod_actor='" + vod_actor + '\'' +
            ", vod_remarks='" + vod_remarks + '\'' +
            ", vod_year='" + vod_year + '\'' +
            ", vod_content='" + vod_content + '\'' +
            ", vod_total='" + vod_total + '\'' +
            '}';
    }
}
