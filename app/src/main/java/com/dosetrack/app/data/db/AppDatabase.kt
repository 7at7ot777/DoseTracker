package com.dosetrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, MedicationEntity::class, ScheduleEntity::class, DoseEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseDao(): DoseDao

    companion object {
        const val NAME = "dosetrack.db"

        /** v4: per-medication reminder style (notification or alarm). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN reminderStyle TEXT")
            }
        }

        /**
         * v5: profiles. Existing medications move to profile 1 ("Me", renamable in the app).
         * Statements mirror what Room generates for ProfileEntity and the profileId index;
         * MigrationTest (Robolectric) validates the result against the entities on CI.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER, " +
                        "`photoPath` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `profiles` (`id`, `name`, `colorArgb`, `photoPath`, `createdAt`, `updatedAt`) " +
                        "VALUES (1, 'Me', NULL, NULL, " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000, CAST(strftime('%s','now') AS INTEGER) * 1000)",
                )
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medications_profileId` ON `medications` (`profileId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_4_5)

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                // v1-v2 were pre-release builds with no user-facing data entry.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
    }
}
