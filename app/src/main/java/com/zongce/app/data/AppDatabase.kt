package com.zongce.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AwardRecord::class, AwardPhoto::class],
    version = 1,
    // 导出 schema 基线到 app/schemas/，为将来写迁移做准备。版本仍是 1，这里不写迁移。
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun awardDao(): AwardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zongce.db"
                ).build().also { INSTANCE = it }
            }
    }
}
