package com.dlight.network.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Vod (Video on Demand) 视频数据模型
 */
public class Vod implements Parcelable {

    protected int vodId;
    protected String vodName;
    protected String vodPic;
    protected String vodPlayUrl;
    protected String vodActor;
    protected String vodRemarks;  // 目前更新到多少级
    protected String vodYear;
    protected String vodContent;
    protected String vodTotal;   // 总集数

    // 构造方法
    public Vod() {}

    public Vod(int vodId, String vodName, String vodPic, String vodPlayUrl,
               String vodActor, String vodRemarks, String vodYear, String vodContent, String vodTotal) {
        this.vodId = vodId;
        this.vodName = vodName;
        this.vodPic = vodPic;
        this.vodPlayUrl = vodPlayUrl;
        this.vodActor = vodActor;
        this.vodRemarks = vodRemarks;
        this.vodYear = vodYear;
        this.vodContent = vodContent;
        this.vodTotal = vodTotal;
    }

    // Parcelable 构造方法
    protected Vod(Parcel in) {
        vodId = in.readInt();
        vodName = in.readString();
        vodPic = in.readString();
        vodPlayUrl = in.readString();
        vodActor = in.readString();
        vodRemarks = in.readString();
        vodYear = in.readString();
        vodContent = in.readString();
        vodTotal = in.readString();
    }

    // 实现 Parcelable 接口的方法
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(vodId);
        dest.writeString(vodName);
        dest.writeString(vodPic);
        dest.writeString(vodPlayUrl);
        dest.writeString(vodActor);
        dest.writeString(vodRemarks);
        dest.writeString(vodYear);
        dest.writeString(vodContent);
        dest.writeString(vodTotal);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Vod> CREATOR = new Creator<Vod>() {
        @Override
        public Vod createFromParcel(Parcel in) {
            return new Vod(in);
        }

        @Override
        public Vod[] newArray(int size) {
            return new Vod[size];
        }
    };

    // Getter 和 Setter 方法
    public int getVodId() {
        return vodId;
    }

    public void setVodId(int vodId) {
        this.vodId = vodId;
    }

    public String getVodName() {
        return vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodPic() {
        return vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodPlayUrl() {
        return vodPlayUrl;
    }

    public void setVodPlayUrl(String vodPlayUrl) {
        this.vodPlayUrl = vodPlayUrl;
    }

    public String getVodActor() {
        return vodActor;
    }

    public void setVodActor(String vodActor) {
        this.vodActor = vodActor;
    }

    public String getVodRemarks() {
        return vodRemarks;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public String getVodYear() {
        return vodYear;
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public String getVodContent() {
        return vodContent;
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public String getVodTotal() {
        return vodTotal;
    }

    public void setVodTotal(String vodTotal) {
        this.vodTotal = vodTotal;
    }

    @Override
    public String toString() {
        return "Vod{" +
                "vodId=" + vodId +
                ", vodName='" + vodName + '\'' +
                ", vodPic='" + vodPic + '\'' +
                ", vodPlayUrl='" + vodPlayUrl + '\'' +
                ", vodActor='" + vodActor + '\'' +
                ", vodRemarks='" + vodRemarks + '\'' +
                ", vodYear='" + vodYear + '\'' +
                ", vodContent='" + vodContent + '\'' +
                ", vodTotal='" + vodTotal + '\'' +
                '}';
    }
}
