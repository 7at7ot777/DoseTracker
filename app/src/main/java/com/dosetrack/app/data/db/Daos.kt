package com.dosetrack.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dosetrack.app.data.model.MedicationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileEntity>)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<MedicationEntity>

    @Query("UPDATE medications SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: MedicationStatus, now: Long)

    @Query("UPDATE medications SET status = :status, scheduleEffectiveFrom = :now, updatedAt = :now WHERE id = :id")
    suspend fun setStatusAndEffectiveFrom(id: Long, status: MedicationStatus, now: Long)

    @Transaction
    @Query("SELECT * FROM medications WHERE profileId = :profileId AND status != 'ARCHIVED' ORDER BY name COLLATE NOCASE")
    fun observeVisibleWithSchedules(profileId: Long): Flow<List<MedicationWithSchedules>>

    @Query("SELECT profileId, COUNT(*) AS count FROM medications WHERE status != 'ARCHIVED' GROUP BY profileId")
    fun observeCountsPerProfile(): Flow<List<ProfileMedicationCount>>

    @Query("DELETE FROM medications WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    /** Any active medication (any profile) in alarm mode — full-screen alarm permission matters. */
    @Query("SELECT EXISTS(SELECT 1 FROM medications WHERE status = 'ACTIVE' AND reminderStyle = 'ALARM')")
    fun observeAnyAlarmMode(): Flow<Boolean>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeWithSchedules(id: Long): Flow<MedicationWithSchedules?>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getWithSchedules(id: Long): MedicationWithSchedules?

    @Transaction
    @Query("SELECT * FROM medications WHERE status = 'ACTIVE'")
    suspend fun getActiveWithSchedules(): List<MedicationWithSchedules>

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Query("SELECT * FROM medication_schedules WHERE medicationId = :medicationId ORDER BY timeOfDay")
    suspend fun getForMedication(medicationId: Long): List<ScheduleEntity>

    @Query("DELETE FROM medication_schedules WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Query("SELECT * FROM medication_schedules")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("DELETE FROM medication_schedules")
    suspend fun deleteAll()
}

@Dao
interface DoseDao {
    /** Idempotent generation: the unique (medicationId, scheduledAt) index drops duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(doses: List<DoseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplacing(doses: List<DoseEntity>)

    @Update
    suspend fun update(dose: DoseEntity)

    @Query("SELECT * FROM doses WHERE id = :id")
    suspend fun getById(id: Long): DoseEntity?

    @Transaction
    @Query("SELECT * FROM doses WHERE id = :id")
    suspend fun getWithMedication(id: Long): DoseWithMedication?

    /** All profiles (Today's "Everyone" view). */
    @Transaction
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt, id")
    fun observeInRange(from: Long, to: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query(
        "SELECT doses.* FROM doses INNER JOIN medications ON medications.id = doses.medicationId " +
            "WHERE medications.profileId = :profileId AND doses.scheduledAt >= :from AND doses.scheduledAt < :to " +
            "ORDER BY doses.scheduledAt, doses.id",
    )
    fun observeInRangeForProfile(from: Long, to: Long, profileId: Long): Flow<List<DoseWithMedication>>

    @Query(
        "SELECT doses.id FROM doses INNER JOIN medications ON medications.id = doses.medicationId " +
            "WHERE medications.profileId = :profileId AND doses.status = 'PENDING'",
    )
    suspend fun getPendingIdsForProfile(profileId: Long): List<Long>

    @Transaction
    @Query("SELECT * FROM doses WHERE medicationId = :medicationId AND scheduledAt < :before ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeRecentForMedication(medicationId: Long, before: Long, limit: Int): Flow<List<DoseEntity>>

    @Query("SELECT MIN(scheduledAt) FROM doses WHERE medicationId = :medicationId AND status = 'PENDING' AND scheduledAt >= :from")
    fun observeNextPendingTime(medicationId: Long, from: Long): Flow<Long?>

    @Query(
        "SELECT medicationId, MIN(scheduledAt) AS nextAt FROM doses " +
            "WHERE status = 'PENDING' AND scheduledAt >= :from GROUP BY medicationId",
    )
    fun observeNextPendingPerMedication(from: Long): Flow<List<NextDose>>

    @Transaction
    @Query("SELECT * FROM doses WHERE status = 'PENDING' AND scheduledAt < :to ORDER BY scheduledAt")
    suspend fun getPendingBefore(to: Long): List<DoseWithMedication>

    /** Missed clock starts at the later of scheduledAt and snoozedUntil (see domain.missedClockStart). */
    @Query(
        "SELECT id FROM doses WHERE status = 'PENDING' " +
            "AND MAX(scheduledAt, COALESCE(snoozedUntil, scheduledAt)) < :threshold",
    )
    suspend fun getPendingIdsOlderThan(threshold: Long): List<Long>

    @Query(
        "UPDATE doses SET status = 'MISSED', updatedAt = :now WHERE status = 'PENDING' " +
            "AND MAX(scheduledAt, COALESCE(snoozedUntil, scheduledAt)) < :threshold",
    )
    suspend fun markPendingMissedOlderThan(threshold: Long, now: Long)

    @Query("SELECT id FROM doses WHERE medicationId = :medicationId AND status = 'PENDING' AND scheduledAt >= :from")
    suspend fun getFuturePendingIds(medicationId: Long, from: Long): List<Long>

    @Query("DELETE FROM doses WHERE medicationId = :medicationId AND status = 'PENDING' AND scheduledAt >= :from")
    suspend fun deleteFuturePending(medicationId: Long, from: Long)

    @Query("SELECT status, COUNT(*) AS count FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to GROUP BY status")
    fun observeStatusCounts(from: Long, to: Long): Flow<List<StatusCount>>

    @Query("SELECT status, COUNT(*) AS count FROM doses WHERE medicationId = :medicationId GROUP BY status")
    fun observeStatusCountsForMedication(medicationId: Long): Flow<List<StatusCount>>

    @Query("SELECT * FROM doses")
    suspend fun getAll(): List<DoseEntity>

    @Query("SELECT id FROM doses WHERE status = 'PENDING'")
    suspend fun getAllPendingIds(): List<Long>

    @Query("DELETE FROM doses")
    suspend fun deleteAll()
}
