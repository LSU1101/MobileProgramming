package com.wherepaid.validator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PaymentRecord::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wherepaid.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: 테스트 알림 컬럼 추가. 기존 기록은 그대로 유지된다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payment_record ADD COLUMN isTest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payment_record ADD COLUMN testScheduledAt INTEGER")
            }
        }
    }
}
