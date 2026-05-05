package com.dlight.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import java.util.List;


@Dao
public interface MyStarRecordDao {
    @Upsert
    void upsert(MyStarRecord myStarRecord);

    @Insert
    void insert(MyStarRecord myStarRecord); // 插入记录


    @Insert
    void insert(List<MyStarRecord> myStarRecordList);
    @Update
    void update(MyStarRecord myStarRecord); // 更新记录

    @Delete
    void delete(MyStarRecord myStarRecord); // 删除记录


    @Query("SELECT * FROM myStar_records WHERE vod_id = :vodId")
    MyStarRecord getStarRecordByVideoId(int vodId); // 根据视频ID获取播放记录

    @Query("SELECT * FROM myStar_records ORDER BY id DESC")
    List<MyStarRecord> getAllStarRecords(); // 获取所有播放记录

    // 可选扩展：根据用户ID和视频ID获取记录（适合多用户）
    @Query("SELECT * FROM myStar_records WHERE userId = :userId AND vod_id = :vodId")
    MyStarRecord getStarsByUserAndVideo(int userId, int vodId);
    @Query("DELETE FROM myStar_records")
    void deleteAllStarRecords(); // 删除所有播放记录

    @Query("SELECT COUNT(vod_id) FROM myStar_records WHERE userId = :userId")
    int countStarRecords(int userId);
    // 可选扩展：根据用户ID和视频ID获取记录（适合多用户）
    @Query("SELECT * FROM myStar_records WHERE userId = :userId ")
    MyStarRecord getStarRecordByUserAndVideo(int userId);

    @Query("DELETE FROM myStar_records WHERE vod_id =:vod_id")
    void deleteStarRecords(int vod_id); //

    @Query("SELECT * FROM myStar_records WHERE isSynced = 0")
    List<MyStarRecord> getUnsyncedRecords();

    @Query("UPDATE myStar_records SET isSynced = 1 WHERE id IN (:ids)")
    void markRecordsAsSynced(List<Integer> ids);
}
