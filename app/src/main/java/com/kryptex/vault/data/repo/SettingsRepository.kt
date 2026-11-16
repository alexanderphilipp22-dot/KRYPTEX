package com.kryptex.vault.data.repo

import com.kryptex.vault.data.db.dao.SettingDao
import com.kryptex.vault.i18n.AppLanguage
import com.kryptex.vault.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Typed access to user preferences stored inside the encrypted database. */
class SettingsRepository(private val dao: SettingDao) {

    /** Seconds of inactivity before auto-lock; 0 means lock only when the app leaves the foreground. */
    val autoLockSeconds: Flow<Int> =
        dao.observe(KEY_AUTO_LOCK).map { it?.toIntOrNull() ?: DEFAULT_AUTO_LOCK_SECONDS }

    /** MediaStore folder under which carrier images are saved, relative to the storage root. */
    val saveFolder: Flow<String> = dao.observe(KEY_SAVE_FOLDER).map { it ?: DEFAULT_SAVE_FOLDER }

    /** The language the user picked, or null while they have not (the UI then follows the phone). */
    val language: Flow<AppLanguage?> = dao.observe(KEY_LANGUAGE).map { AppLanguage.fromCode(it) }

    /** Whether short help tips are shown on the Hide, Reveal and Settings screens. */
    val showHelpBubbles: Flow<Boolean> =
        dao.observe(KEY_HELP_BUBBLES).map { it?.toBooleanStrictOrNull() ?: DEFAULT_SHOW_HELP_BUBBLES }

    suspend fun setLanguage(language: AppLanguage) {
        dao.upsert(SettingEntity(KEY_LANGUAGE, language.code))
    }

    suspend fun setShowHelpBubbles(show: Boolean) {
        dao.upsert(SettingEntity(KEY_HELP_BUBBLES, show.toString()))
    }

    suspend fun setAutoLockSeconds(seconds: Int) {
        require(seconds >= 0) { "negative timeout" }
        dao.upsert(SettingEntity(KEY_AUTO_LOCK, seconds.toString()))
    }

    suspend fun setSaveFolder(folder: String) {
        require(folder.isNotBlank()) { "empty folder" }
        dao.upsert(SettingEntity(KEY_SAVE_FOLDER, folder.trim()))
    }

    suspend fun currentSaveFolder(): String = dao.get(KEY_SAVE_FOLDER) ?: DEFAULT_SAVE_FOLDER

    companion object {
        const val KEY_AUTO_LOCK = "auto_lock_seconds"
        const val KEY_SAVE_FOLDER = "save_folder"
        const val KEY_LANGUAGE = "language"
        const val KEY_HELP_BUBBLES = "show_help_bubbles"
        const val DEFAULT_SHOW_HELP_BUBBLES = true
        const val DEFAULT_AUTO_LOCK_SECONDS = 60

        /** Plain "Pictures": no app-named album that would give the carriers away. */
        const val DEFAULT_SAVE_FOLDER = "Pictures"
    }
}
