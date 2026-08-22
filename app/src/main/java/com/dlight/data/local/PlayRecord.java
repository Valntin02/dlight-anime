package com.dlight.data.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.dlight.data.model.VodData;
import com.dlight.network.LocalOnly;
import com.google.gson.JsonParser;

@Entity(
    tableName = "play_records",
    indices = {@Index(value = {"userId", "vod_id"}, unique = true)} // 联合唯一索引
)
public class PlayRecord {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int userId;
    private int vod_id;
    private String vod_name;
    private String vod_pic;
    private String vod_play_url;
    @LocalOnly
    private String vod_play_data;
    private String vod_actor;
    private String vod_remarks;
    private String vod_year;
    private String vod_content;
    private String vod_total;

    private int episodeIndex;

    private boolean isSynced = false; // 默认是未同步的
    // 无参构造（Room要求）
    public PlayRecord() {}

    // 从 VodData 构造
    public PlayRecord(int userId, int episodeIndex, VodData data) {
        this.userId = userId;
        this.episodeIndex = episodeIndex;
        this.vod_id = data.getVod_id();
        this.vod_name = data.getVod_name();
        this.vod_pic = data.getVod_pic();
        this.vod_play_url = data.getVod_play_url();
        this.vod_play_data = data.getVodPlayData() == null
            ? null : data.getVodPlayData().toString();
        this.vod_actor = data.getVod_actor();
        this.vod_remarks = data.getVod_remarks();
        this.vod_year = data.getVod_year();
        this.vod_content = data.getVod_content();
        this.vod_total = data.getVod_total();
    }

    // Getter & Setter（省略部分你可以自动生成）
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public boolean getIsSynced() { return isSynced; }

    public void setIsSynced(boolean isSynced){this.isSynced=isSynced;}
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getVod_id() { return vod_id; }
    public void setVod_id(int vod_id) { this.vod_id = vod_id; }

    public String getVod_name() { return vod_name; }
    public void setVod_name(String vod_name) { this.vod_name = vod_name; }

    public String getVod_pic() { return vod_pic; }
    public void setVod_pic(String vod_pic) { this.vod_pic = vod_pic; }

    public String getVod_play_url() { return vod_play_url; }
    public void setVod_play_url(String vod_play_url) { this.vod_play_url = vod_play_url; }

    public String getVod_play_data() { return vod_play_data; }
    public void setVod_play_data(String vod_play_data) { this.vod_play_data = vod_play_data; }

    public String getVod_actor() { return vod_actor; }
    public void setVod_actor(String vod_actor) { this.vod_actor = vod_actor; }

    public String getVod_remarks() { return vod_remarks; }
    public void setVod_remarks(String vod_remarks) { this.vod_remarks = vod_remarks; }

    public String getVod_year() { return vod_year; }
    public void setVod_year(String vod_year) { this.vod_year = vod_year; }

    public String getVod_content() { return vod_content; }
    public void setVod_content(String vod_content) { this.vod_content = vod_content; }

    public String getVod_total() { return vod_total; }
    public void setVod_total(String vod_total) { this.vod_total = vod_total; }

    public int getEpisodeIndex() { return episodeIndex; }
    public void setEpisodeIndex(int episodeIndex) { this.episodeIndex = episodeIndex; }

    public VodData toVodData() {
        VodData data = new VodData(
            vod_id, vod_name, vod_pic, vod_play_url, vod_actor,
            vod_remarks, vod_year, vod_content, vod_total
        );
        if (vod_play_data != null && !vod_play_data.trim().isEmpty()) {
            try {
                data.setVodPlayData(new JsonParser().parse(vod_play_data));
            } catch (RuntimeException ignored) {
                data.setVodPlayData(null);
            }
        }
        return data;
    }
}
