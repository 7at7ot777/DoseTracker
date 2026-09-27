package com.dosetrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationEntity::class, ScheduleEntity::class, DoseEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseDao(): DoseDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dosetrack.db")
                // v1 was a pre-release build with no user-facing data entry; later versions need real migrations.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
