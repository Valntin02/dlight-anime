package com.dlight.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

//这里添加表了之后需要在这里增加实体类不然找不到
@Database(entities = {PlayRecord.class, MyStarRecord.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    static final String CANONICAL_DB_NAME = "play_record_db";
    static final String LEGACY_DB_NAME = "myStar_records";

    private static AppDatabase instance;

    public abstract PlayRecordDao playRecordDao(); // 获取 DAO

    public abstract MyStarRecordDao myStarRecordDao();

    public static synchronized AppDatabase getInstancePlayRecord(Context context) {
        return getInstance(context);
    }

    public static synchronized AppDatabase getInstanceMyStarRecord(Context context) {
        return getInstance(context);
    }

    private static AppDatabase getInstance(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (instance == null) {
            instance = Room.databaseBuilder(applicationContext,
                    AppDatabase.class, CANONICAL_DB_NAME)
                .build();
        }
        LegacyRecordImporter.importIfNeeded(applicationContext, instance);
        return instance;
    }

    static synchronized void resetInstanceForTests() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
