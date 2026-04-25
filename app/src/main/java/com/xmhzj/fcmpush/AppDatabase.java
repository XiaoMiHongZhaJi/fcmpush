package com.xmhzj.fcmpush;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MessageModel.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase INSTANCE;

    public abstract MessageDao messageDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context, AppDatabase.class, "app_messages.db")
                    .addMigrations(MIGRATION_1_2) // ✅ 必须加
                    .build();
        }
        return INSTANCE;
    }
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 给表增加新字段，设置默认值
            database.execSQL(
                    "ALTER TABLE messages ADD COLUMN packageName TEXT"
            );
        }
    };
}
