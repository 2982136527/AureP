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
    val subtitleMode: String = SUBTITLE_MODE_DEFAULT,
    val layoutMode: String = LAYOUT_MODE_DEFAULT,
    val showLibraryCardTitle: Boolean = SHOW_LIBRARY_CARD_TITLE_DEFAULT,
    val librarySortMode: String = LIBRARY_SORT_MODE_DEFAULT,
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
const val SUBTITLE_MODE_DEFAULT = "双语优先"
const val LAYOUT_MODE_DEFAULT = "编辑卡片流"
const val SHOW_LIBRARY_CARD_TITLE_DEFAULT = true
const val LIBRARY_SORT_MODE_DEFAULT = "最近更新"
const val LIBRARY_SORT_MODE_NAME = "名称 A-Z"
const val LIBRARY_SORT_MODE_RATING = "评分最高"

val LIBRARY_SORT_MODES = listOf(
    LIBRARY_SORT_MODE_DEFAULT,
    LIBRARY_SORT_MODE_NAME,
    LIBRARY_SORT_MODE_RATING,
)

fun normalizePlayerMode(value: String): String = when (value) {
    "ExoPlayer + mpv" -> PLAYER_MODE_COMPATIBILITY
    "ExoPlayer" -> PLAYER_MODE_STANDARD
    "系统解码优先" -> PLAYER_MODE_SYSTEM
    "系统直解优先" -> PLAYER_MODE_SYSTEM
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
        AppSettings(
            playerMode = normalizePlayerMode(preferences[Keys.PlayerMode] ?: PLAYER_MODE_DEFAULT),
            subtitleMode = preferences[Keys.SubtitleMode] ?: SUBTITLE_MODE_DEFAULT,
            layoutMode = normalizeLayoutMode(preferences[Keys.LayoutMode] ?: LAYOUT_MODE_DEFAULT),
            showLibraryCardTitle = preferences[Keys.ShowLibraryCardTitle] ?: SHOW_LIBRARY_CARD_TITLE_DEFAULT,
            librarySortMode = normalizeLibrarySortMode(preferences[Keys.LibrarySortMode] ?: LIBRARY_SORT_MODE_DEFAULT),
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun updatePlayerMode(value: String) {
        dataStore.edit { it[Keys.PlayerMode] = normalizePlayerMode(value) }
    }

    suspend fun updateSubtitleMode(value: String) {
        dataStore.edit { it[Keys.SubtitleMode] = value }
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

    private object Keys {
        val PlayerMode = stringPreferencesKey("player_mode")
        val SubtitleMode = stringPreferencesKey("subtitle_mode")
        val LayoutMode = stringPreferencesKey("layout_mode")
        val ShowLibraryCardTitle = booleanPreferencesKey("show_library_card_title")
        val LibrarySortMode = stringPreferencesKey("library_sort_mode")
    }
}
