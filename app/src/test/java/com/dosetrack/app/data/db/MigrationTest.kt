package com.dosetrack.app.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Upgrades a real version-4 database to version 5. Room validates the migrated schema against the
 * entities when it opens the database, so any mismatch in MIGRATION_4_5 fails this test.
 *
 * The v4 database is derived from Room's own v5 schema minus the v5 additions, so the unchanged
 * tables are exactly what Room generated for v4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate4To5KeepsDataUnderDefaultProfile() {
        // 1. Let Room create the current schema, then turn it back into the v4 layout.
        AppDatabase.build(context, dbName).apply {
            openHelper.writableDatabase
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Robolectric's SQLite lacks ALTER TABLE DROP COLUMN, so recreate the (still empty)
            // medications table from Room's own DDL without the v5 profileId column.
            val v5Ddl = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'medications'",
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            val v4Ddl = v5Ddl.replace(", `profileId` INTEGER NOT NULL DEFAULT 1", "")
            check(v4Ddl != v5Ddl) { "profileId column not found in Room's DDL: $v5Ddl" }
            // Foreign keys are off on this raw connection, so the parent table can be replaced.
            db.execSQL("DROP INDEX `index_medications_profileId`")
            db.execSQL("DROP TABLE `medications`")
            db.execSQL(v4Ddl)
            db.execSQL("DROP TABLE `profiles`")
            db.execSQL("DROP TABLE IF EXISTS `room_master_table`")
            db.execSQL(
                "INSERT INTO medications (id, name, dosageAmount, dosageUnit, medicationType, frequency, " +
                    "foodInstruction, startDate, durationType, status, scheduleEffectiveFrom, createdAt, updatedAt, reminderStyle) " +
                    "VALUES (7, 'Vitamin D', 1.0, 'TABLET', 'TABLET', 'ONCE_DAILY', 'ANY_TIME', 20000, 'ONGOING', " +
                    "'ACTIVE', 1, 1, 1, 'ALARM')",
            )
            db.execSQL(
                "INSERT INTO medication_schedules (id, medicationId, timeOfDay, note, createdAt, updatedAt) " +
                    "VALUES (3, 7, 540, 'After lunch', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO doses (id, medicationId, scheduledAt, takenAt, status, createdAt, updatedAt) " +
                    "VALUES (11, 7, 5000, 5100, 'TAKEN', 1, 1)",
            )
            db.version = 4
        }

        // 2. Open with Room: runs MIGRATION_4_5, then validates every table against the entities.
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val profiles = migrated.profileDao().getAll()
                assertEquals(1, profiles.size)
                assertEquals(1L, profiles[0].id)
                assertEquals("Me", profiles[0].name)

                val medication = migrated.medicationDao().getById(7)!!
                assertEquals(1L, medication.profileId)
                assertEquals("Vitamin D", medication.name)

                assertEquals("After lunch", migrated.scheduleDao().getForMedication(7).single().note)
                assertEquals(5100L, migrated.doseDao().getById(11)!!.takenAt)
            }
        } finally {
            migrated.close()
        }
    }
}
