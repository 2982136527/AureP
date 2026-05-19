package com.qiuhu.embyflow.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val playerMode: String = PLAYER_MODE_DEFAULT,
    val embeddedSubtitleLanguage: String = SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
    val externalSubtitleLanguage: String = SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
    val layoutMode: String = LAYOUT_MODE_DEFAULT,
    val showLibraryCardTitle: Boolean = SHOW_LIBRARY_CARD_TITLE_DEFAULT,
    val librarySortMode: String = LIBRARY_SORT_MODE_DEFAULT,
    val experimentalDualBackendRace: Boolean = EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT,
)

data class LibrarySortSpec(
    val sortBy: String,
    val sortOrder: String,
)

fun AppSettings.isLargeCardLayout(): Boolean = layoutMode == "大图优先"

fun AppSettings.isCompactLayout(): Boolean = layoutMode == "紧凑信息流"

const val PLAYER_MODE_COMPATIBILITY = "兼容优先"
const val PLAYER_MODE_STANDARD = "标准模式"
const val PLAYER_MODE_SYSTEM = "快速起播"
const val PLAYER_MODE_DEFAULT = PLAYER_MODE_COMPATIBILITY
const val SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT = "跟随默认"
const val SUBTITLE_LANGUAGE_PREFERENCE_CHINESE = "中文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE = "简体中文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE = "繁体中文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH = "英文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE = "日文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_KOREAN = "韩文优先"
const val SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT = SUBTITLE_LANGUAGE_PREFERENCE_CHINESE
val SUBTITLE_LANGUAGE_PREFERENCES = listOf(
    SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT,
    SUBTITLE_LANGUAGE_PREFERENCE_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH,
    SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE,
    SUBTITLE_LANGUAGE_PREFERENCE_KOREAN,
)
const val LAYOUT_MODE_DEFAULT = "编辑卡片流"
const val SHOW_LIBRARY_CARD_TITLE_DEFAULT = true
const val LIBRARY_SORT_MODE_DEFAULT = "最近更新"
const val LIBRARY_SORT_MODE_NAME = "名称 A-Z"
const val LIBRARY_SORT_MODE_RATING = "评分最高"
const val EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT = false

val LIBRARY_SORT_MODES = listOf(
    LIBRARY_SORT_MODE_DEFAULT,
    LIBRARY_SORT_MODE_NAME,
    LIBRARY_SORT_MODE_RATING,
)

fun normalizePlayerMode(value: String): String = when (value) {
    "ExoPlayer + mpv" -> PLAYER_MODE_COMPATIBILITY
    "ExoPlayer" -> PLAYER_MODE_STANDARD
    // Migrate old Exo-first labels to the current VLC-first default strategy.
    "系统解码优先" -> PLAYER_MODE_STANDARD
    "系统直解优先" -> PLAYER_MODE_STANDARD
    PLAYER_MODE_COMPATIBILITY,
    PLAYER_MODE_STANDARD,
    PLAYER_MODE_SYSTEM,
    -> value
    else -> PLAYER_MODE_DEFAULT
}

fun normalizeLibrarySortMode(value: String): String = when (value) {
    LIBRARY_SORT_MODE_DEFAULT,
    LIBRARY_SORT_MODE_NAME,
    LIBRARY_SORT_MODE_RATING,
    -> value
    else -> LIBRARY_SORT_MODE_DEFAULT
}

fun normalizeSubtitleLanguagePreference(value: String): String = when (value) {
    SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT,
    SUBTITLE_LANGUAGE_PREFERENCE_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE,
    SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH,
    SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE,
    SUBTITLE_LANGUAGE_PREFERENCE_KOREAN,
    -> value
    "双语优先" -> SUBTITLE_LANGUAGE_PREFERENCE_CHINESE
    "原语言优先" -> SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT
    "仅外挂字幕" -> SUBTITLE_LANGUAGE_PREFERENCE_CHINESE
    "关闭自动匹配" -> SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT
    "中文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_CHINESE
    "简体中文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE
    "繁体中文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE
    "英文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH
    "日文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE
    "韩文优先" -> SUBTITLE_LANGUAGE_PREFERENCE_KOREAN
    else -> SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT
}

fun normalizeLayoutMode(value: String): String = when (value) {
    "编辑卡片风" -> LAYOUT_MODE_DEFAULT
    LAYOUT_MODE_DEFAULT,
    "大图优先",
    "紧凑信息流",
    -> value
    else -> LAYOUT_MODE_DEFAULT
}

fun librarySortSpec(mode: String): LibrarySortSpec = when (normalizeLibrarySortMode(mode)) {
    LIBRARY_SORT_MODE_NAME -> LibrarySortSpec(
        sortBy = "SortName",
        sortOrder = "Ascending",
    )

    LIBRARY_SORT_MODE_RATING -> LibrarySortSpec(
        sortBy = "CommunityRating,SortName",
        sortOrder = "Descending",
    )

    else -> LibrarySortSpec(
        sortBy = "DateCreated,SortName",
        sortOrder = "Descending",
    )
}

class AppSettingsStore(
    context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val legacySubtitlePreference = preferences[Keys.SubtitleMode]
        AppSettings(
            playerMode = normalizePlayerMode(preferences[Keys.PlayerMode] ?: PLAYER_MODE_DEFAULT),
            embeddedSubtitleLanguage = normalizeSubtitleLanguagePreference(
                preferences[Keys.EmbeddedSubtitleLanguage]
                    ?: legacySubtitlePreference
                    ?: SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
            ),
            externalSubtitleLanguage = normalizeSubtitleLanguagePreference(
                preferences[Keys.ExternalSubtitleLanguage]
                    ?: legacySubtitlePreference
                    ?: SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
            ),
            layoutMode = normalizeLayoutMode(preferences[Keys.LayoutMode] ?: LAYOUT_MODE_DEFAULT),
            showLibraryCardTitle = preferences[Keys.ShowLibraryCardTitle] ?: SHOW_LIBRARY_CARD_TITLE_DEFAULT,
            librarySortMode = normalizeLibrarySortMode(preferences[Keys.LibrarySortMode] ?: LIBRARY_SORT_MODE_DEFAULT),
            experimentalDualBackendRace = preferences[Keys.ExperimentalDualBackendRace] ?: EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT,
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun updatePlayerMode(value: String) {
        dataStore.edit { it[Keys.PlayerMode] = normalizePlayerMode(value) }
    }

    suspend fun updateEmbeddedSubtitleLanguage(value: String) {
        dataStore.edit { it[Keys.EmbeddedSubtitleLanguage] = normalizeSubtitleLanguagePreference(value) }
    }

    suspend fun updateExternalSubtitleLanguage(value: String) {
        dataStore.edit { it[Keys.ExternalSubtitleLanguage] = normalizeSubtitleLanguagePreference(value) }
    }

    suspend fun updateLayoutMode(value: String) {
        dataStore.edit { it[Keys.LayoutMode] = normalizeLayoutMode(value) }
    }

    suspend fun updateShowLibraryCardTitle(value: Boolean) {
        dataStore.edit { it[Keys.ShowLibraryCardTitle] = value }
    }

    suspend fun updateLibrarySortMode(value: String) {
        dataStore.edit { it[Keys.LibrarySortMode] = normalizeLibrarySortMode(value) }
    }

    suspend fun updateExperimentalDualBackendRace(value: Boolean) {
        dataStore.edit { it[Keys.ExperimentalDualBackendRace] = value }
    }

    private object Keys {
        val PlayerMode = stringPreferencesKey("player_mode")
        val SubtitleMode = stringPreferencesKey("subtitle_mode")
        val EmbeddedSubtitleLanguage = stringPreferencesKey("embedded_subtitle_language")
        val ExternalSubtitleLanguage = stringPreferencesKey("external_subtitle_language")
        val LayoutMode = stringPreferencesKey("layout_mode")
        val ShowLibraryCardTitle = booleanPreferencesKey("show_library_card_title")
        val LibrarySortMode = stringPreferencesKey("library_sort_mode")
        val ExperimentalDualBackendRace = booleanPreferencesKey("experimental_dual_backend_race")
    }
}
