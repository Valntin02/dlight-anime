package com.dlight.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface PlayRecordDao {
    @Upsert
    void upsert(PlayRecord playRecord);

    @Insert
    void insert(PlayRecord playRecord); // 插入记录
    @Insert
    void insert(List<PlayRecord> playRecordList);
    @Update
    void update(PlayRecord playRecord); // 更新记录

    @Delete
    void delete(PlayRecord playRecord); // 删除记录

    @Query("SELECT * FROM play_records WHERE vod_id = :vodId")
    PlayRecord getPlayRecordByVideoId(int vodId); // 根据视频ID获取播放记录

    @Query("SELECT * FROM play_records ORDER BY id DESC")
    List<PlayRecord> getAllPlayRecords(); // 获取所有播放记录

    @Query("DELETE FROM play_records")
    void deleteAllPlayRecords(); // 删除所有播放记录

    // 可选扩展：根据用户ID和视频ID获取记录（适合多用户）
    @Query("SELECT * FROM play_records WHERE userId = :userId AND vod_id = :vodId")
    PlayRecord getPlayRecordByUserAndVideo(int userId, int vodId);

    @Query("UPDATE play_records SET episodeIndex = :episodeIndex, isSynced = 0 WHERE userId = :userId AND vod_id = :vodId")
    void updateEpisode(int userId, int vodId, int episodeIndex);

    @Query("SELECT * FROM play_records WHERE isSynced = 0")
    List<PlayRecord> getUnsyncedRecords();

    @Query("UPDATE play_records SET isSynced = 1 WHERE id IN (:ids)")
    void markRecordsAsSynced(List<Integer> ids);
}
