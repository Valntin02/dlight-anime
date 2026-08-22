package com.dlight.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

//这里添加表了之后需要在这里增加实体类不然找不到
@Database(entities = {PlayRecord.class, MyStarRecord.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    static final String CANONICAL_DB_NAME = "play_record_db";
    static final String LEGACY_DB_NAME = "myStar_records";

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE play_records ADD COLUMN vod_play_data TEXT");
            database.execSQL("ALTER TABLE myStar_records ADD COLUMN vod_play_data TEXT");
        }
    };

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
                .addMigrations(MIGRATION_1_2)
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
