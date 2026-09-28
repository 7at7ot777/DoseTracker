package com.dosetrack.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.dosetrack.app.data.db.AppDatabase
import com.dosetrack.app.data.db.ProfileEntity
import com.dosetrack.app.data.db.ProfileMedicationCount
import com.dosetrack.app.domain.LastProfileException
import com.dosetrack.app.domain.ProfileLimitReachedException
import com.dosetrack.app.domain.ProfileRules
import com.dosetrack.app.reminders.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** What the Today screen shows: one profile, or everyone. */
data class ProfileSelection(val profile: ProfileEntity, val everyone: Boolean)

class ProfileRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val reminders: ReminderManager,
    private val contentResolver: ContentResolver,
    private val avatarDir: File,
    private val defaultProfileName: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val profileDao = db.profileDao()

    fun observeAll(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun observeMedicationCounts(): Flow<List<ProfileMedicationCount>> = db.medicationDao().observeCountsPerProfile()

    suspend fun get(id: Long): ProfileEntity? = profileDao.getById(id)

    suspend fun getAll(): List<ProfileEntity> = profileDao.getAll()

    /**
     * The profile the UI is showing. Falls back to the first profile when the stored id no
     * longer exists (e.g. after deleting the active profile or restoring a backup).
     */
    val selection: Flow<ProfileSelection> = combine(
        profileDao.observeAll().filter { it.isNotEmpty() },
        settingsRepository.settings.map { it.activeProfileId to it.todayShowsEveryone }.distinctUntilChanged(),
    ) { profiles, (activeId, everyone) ->
        ProfileSelection(profiles.firstOrNull { it.id == activeId } ?: profiles.first(), everyone)
    }.distinctUntilChanged()

    val activeProfile: Flow<ProfileEntity> = selection.map { it.profile }.distinctUntilChanged()

    suspend fun activeProfileNow(): ProfileEntity {
        ensureDefaultProfile()
        val activeId = settingsRepository.current().activeProfileId
        val profiles = profileDao.getAll()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    /** Guarantees at least one profile exists (fresh install, after clearing data). */
    suspend fun ensureDefaultProfile() {
        db.withTransaction {
            if (profileDao.count() == 0) {
                val now = clock()
                profileDao.insert(ProfileEntity(name = defaultProfileName(), createdAt = now, updatedAt = now))
            }
        }
    }

    suspend fun select(profileId: Long) = settingsRepository.setActiveProfile(profileId)

    suspend fun selectEveryone(everyone: Boolean) = settingsRepository.setTodayShowsEveryone(everyone)

    /** @throws ProfileLimitReachedException when [ProfileRules.MAX_PROFILES] already exist. */
    suspend fun create(name: String, colorArgb: Int?): Long = db.withTransaction {
        if (!ProfileRules.canCreate(profileDao.count())) throw ProfileLimitReachedException()
        val now = clock()
        profileDao.insert(ProfileEntity(name = name.trim(), colorArgb = colorArgb, createdAt = now, updatedAt = now))
    }

    suspend fun update(id: Long, name: String, colorArgb: Int?) {
        val profile = profileDao.getById(id) ?: return
        profileDao.update(profile.copy(name = name.trim(), colorArgb = colorArgb, updatedAt = clock()))
    }

    /**
     * Permanently deletes the profile with its medications, schedules and dose history, and
     * cancels its reminders. Other profiles are untouched.
     * @throws LastProfileException for the only remaining profile.
     */
    suspend fun delete(id: Long) {
        val profile = profileDao.getById(id) ?: return
        if (profileDao.count() <= 1) throw LastProfileException()
        reminders.onDosesResolved(db.doseDao().getPendingIdsForProfile(id))
        db.withTransaction {
            // Schedules and doses are removed by their foreign-key cascade on medications.
            db.medicationDao().deleteForProfile(id)
            profileDao.delete(id)
        }
        profile.photoPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
        if (settingsRepository.current().activeProfileId == id) {
            profileDao.getAll().firstOrNull()?.let { settingsRepository.setActiveProfile(it.id) }
        }
    }

    /** Stores a down-scaled square copy of the picked image as the avatar; null removes it. */
    suspend fun setPhoto(id: Long, uri: Uri?) {
        val profile = profileDao.getById(id) ?: return
        val newPath = if (uri == null) null else withContext(Dispatchers.IO) { saveAvatar(id, uri) }
        profileDao.update(profile.copy(photoPath = newPath, updatedAt = clock()))
        profile.photoPath?.takeIf { it != newPath }?.let { withContext(Dispatchers.IO) { File(it).delete() } }
    }

    /** Removes every avatar file (clear all data / restore backup). */
    suspend fun deleteAllAvatarFiles() = withContext(Dispatchers.IO) {
        avatarDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun saveAvatar(profileId: Long, uri: Uri): String {
        val bitmap = decodeScaled(uri) ?: throw IllegalArgumentException("Unreadable image")
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > AVATAR_SIZE) Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true) else square
        avatarDir.mkdirs()
        val file = File(avatarDir, "profile_${profileId}_${clock()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }

    /** ImageDecoder (API 28+) also applies EXIF rotation; older devices use sampled BitmapFactory. */
    private fun decodeScaled(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val shortest = min(info.size.width, info.size.height)
                if (shortest > AVATAR_SIZE * 2) {
                    val scale = AVATAR_SIZE * 2f / shortest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_SIZE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = max(1, sample) }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

    private companion object {
        const val AVATAR_SIZE = 512
    }
}
