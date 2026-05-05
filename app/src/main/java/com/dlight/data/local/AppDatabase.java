package com.dlight.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

//这里添加表了之后需要在这里增加实体类不然找不到
@Database(entities = {PlayRecord.class, MyStarRecord.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract PlayRecordDao playRecordDao(); // 获取 DAO

    public abstract MyStarRecordDao myStarRecordDao();
    public static synchronized AppDatabase getInstancePlayRecord(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "play_record_db")
                .fallbackToDestructiveMigration() // 若数据库版本更新，清空数据库
                .build();
        }
        return instance;
    }

    public static synchronized AppDatabase getInstanceMyStarRecord(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "myStar_records")
                .fallbackToDestructiveMigration() // 若数据库版本更新，清空数据库
                .build();
        }
        return instance;
    }
}
