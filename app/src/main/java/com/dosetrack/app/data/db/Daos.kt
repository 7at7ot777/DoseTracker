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
    @Query("SELECT * FROM medications WHERE status != 'ARCHIVED' ORDER BY name COLLATE NOCASE")
    fun observeVisibleWithSchedules(): Flow<List<MedicationWithSchedules>>

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

    @Transaction
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt, id")
    fun observeInRange(from: Long, to: Long): Flow<List<DoseWithMedication>>

    @Transaction
    @Query("SELECT * FROM doses WHERE medicationId = :medicationId AND scheduledAt < :before ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeRecentForMedication(medicationId: Long, before: Long, limit: Int): Flow<List<DoseEntity>>

    @Query("SELECT MIN(scheduledAt) FROM doses WHERE medicationId = :medicationId AND status = 'PENDING' AND scheduledAt >= :from")
    fun observeNextPendingTime(medicationId: Long, from: Long): Flow<Long?>

    @Transaction
    @Query("SELECT * FROM doses WHERE status = 'PENDING' AND scheduledAt < :to ORDER BY scheduledAt")
    suspend fun getPendingBefore(to: Long): List<DoseWithMedication>

    @Query("SELECT id FROM doses WHERE status = 'PENDING' AND scheduledAt < :threshold")
    suspend fun getPendingIdsOlderThan(threshold: Long): List<Long>

    @Query("UPDATE doses SET status = 'MISSED', updatedAt = :now WHERE status = 'PENDING' AND scheduledAt < :threshold")
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
}
