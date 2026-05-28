package com.qiuhu.embyflow.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val playerMode: String = PLAYER_MODE_DEFAULT,
    val embeddedSubtitleLanguage: String = SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
    val externalSubtitleLanguage: String = SUBTITLE_LANGUAGE_PREFERENCE_DEFAULT,
    val layoutMode: String = LAYOUT_MODE_DEFAULT,
    val showLibraryCardTitle: Boolean = SHOW_LIBRARY_CARD_TITLE_DEFAULT,
    val showEpisodeTitle: Boolean = SHOW_EPISODE_TITLE_DEFAULT,
    val librarySortMode: String = LIBRARY_SORT_MODE_DEFAULT,
    val libraryFilters: Map<String, LibraryFilterSpec> = emptyMap(),
    val experimentalDualBackendRace: Boolean = EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT,
    val homeModuleOrder: List<String> = HOME_MODULE_ORDER_DEFAULT,
    val homeModuleHidden: Set<String> = emptySet(),
    val libraryColumnCount: Int = LIBRARY_COLUMN_COUNT_DEFAULT,
)

data class LibrarySortSpec(
    val sortBy: String,
    val sortOrder: String,
)

@Serializable
data class LibraryFilterSpec(
    val genres: List<String> = emptyList(),
    val years: List<String> = emptyList(),
    val unplayedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
)

val LibraryFilterSpec.isActive: Boolean
    get() = genres.isNotEmpty() || years.isNotEmpty() || unplayedOnly || favoritesOnly

fun AppSettings.isLargeCardLayout(): Boolean = layoutMode == "大图优先"

fun AppSettings.isCompactLayout(): Boolean = layoutMode == "紧凑信息流"

fun AppSettings.libraryFilterFor(libraryId: String): LibraryFilterSpec =
    libraryFilters[libraryId] ?: LibraryFilterSpec()

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
const val SHOW_EPISODE_TITLE_DEFAULT = true
const val LIBRARY_SORT_MODE_DEFAULT = "最近更新"
const val LIBRARY_SORT_MODE_NAME = "名称 A-Z"
const val LIBRARY_SORT_MODE_RATING = "评分最高"
const val EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT = false

const val HOME_MODULE_HERO = "hero"
const val HOME_MODULE_HIGHLIGHTS = "highlights"
const val HOME_MODULE_NEXT_UP = "next_up"
const val HOME_MODULE_CONTINUE_WATCHING = "continue_watching"

val HOME_MODULE_ORDER_DEFAULT = listOf(
    HOME_MODULE_HERO,
    HOME_MODULE_HIGHLIGHTS,
    HOME_MODULE_NEXT_UP,
    HOME_MODULE_CONTINUE_WATCHING,
)

const val LIBRARY_COLUMN_COUNT_DEFAULT = 3
const val LIBRARY_COLUMN_COUNT_MIN = 2
const val LIBRARY_COLUMN_COUNT_MAX = 5

val HOME_MODULE_LABELS = mapOf(
    HOME_MODULE_HERO to "精选海报",
    HOME_MODULE_HIGHLIGHTS to "今日亮点",
    HOME_MODULE_NEXT_UP to "继续播放",
    HOME_MODULE_CONTINUE_WATCHING to "继续观看",
)

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
            showEpisodeTitle = preferences[Keys.ShowEpisodeTitle] ?: SHOW_EPISODE_TITLE_DEFAULT,
            librarySortMode = normalizeLibrarySortMode(preferences[Keys.LibrarySortMode] ?: LIBRARY_SORT_MODE_DEFAULT),
            libraryFilters = runCatching {
                Json.decodeFromString<Map<String, LibraryFilterSpec>>(preferences[Keys.LibraryFilters].orEmpty())
            }.getOrDefault(emptyMap()),
            experimentalDualBackendRace = preferences[Keys.ExperimentalDualBackendRace] ?: EXPERIMENTAL_DUAL_BACKEND_RACE_DEFAULT,
            homeModuleOrder = runCatching {
                val stored = preferences[Keys.HomeModuleOrder].orEmpty()
                if (stored.isNotBlank()) stored.split(",") else HOME_MODULE_ORDER_DEFAULT
            }.getOrDefault(HOME_MODULE_ORDER_DEFAULT),
            homeModuleHidden = runCatching {
                val stored = preferences[Keys.HomeModuleHidden].orEmpty()
                if (stored.isNotBlank()) stored.split(",").toSet() else emptySet()
            }.getOrDefault(emptySet()),
            libraryColumnCount = (preferences[Keys.LibraryColumnCount] ?: LIBRARY_COLUMN_COUNT_DEFAULT)
                .coerceIn(LIBRARY_COLUMN_COUNT_MIN, LIBRARY_COLUMN_COUNT_MAX),
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

    suspend fun updateShowEpisodeTitle(value: Boolean) {
        dataStore.edit { it[Keys.ShowEpisodeTitle] = value }
    }

    suspend fun updateLibrarySortMode(value: String) {
        dataStore.edit { it[Keys.LibrarySortMode] = normalizeLibrarySortMode(value) }
    }

    suspend fun updateLibraryFilter(libraryId: String, filter: LibraryFilterSpec) {
        dataStore.edit { prefs ->
            val current = runCatching {
                Json.decodeFromString<Map<String, LibraryFilterSpec>>(prefs[Keys.LibraryFilters].orEmpty())
            }.getOrDefault(emptyMap())
            val updated = if (filter.isActive) current + (libraryId to filter) else current - libraryId
            prefs[Keys.LibraryFilters] = Json.encodeToString(
                MapSerializer(String.serializer(), LibraryFilterSpec.serializer()),
                updated,
            )
        }
    }

    suspend fun updateExperimentalDualBackendRace(value: Boolean) {
        dataStore.edit { it[Keys.ExperimentalDualBackendRace] = value }
    }

    suspend fun updateHomeModuleOrder(order: List<String>) {
        dataStore.edit { it[Keys.HomeModuleOrder] = order.joinToString(",") }
    }

    suspend fun updateHomeModuleHidden(hidden: Set<String>) {
        dataStore.edit { it[Keys.HomeModuleHidden] = hidden.joinToString(",") }
    }

    suspend fun updateLibraryColumnCount(count: Int) {
        dataStore.edit { it[Keys.LibraryColumnCount] = count.coerceIn(LIBRARY_COLUMN_COUNT_MIN, LIBRARY_COLUMN_COUNT_MAX) }
    }

    private object Keys {
        val PlayerMode = stringPreferencesKey("player_mode")
        val SubtitleMode = stringPreferencesKey("subtitle_mode")
        val EmbeddedSubtitleLanguage = stringPreferencesKey("embedded_subtitle_language")
        val ExternalSubtitleLanguage = stringPreferencesKey("external_subtitle_language")
        val LayoutMode = stringPreferencesKey("layout_mode")
        val ShowLibraryCardTitle = booleanPreferencesKey("show_library_card_title")
        val ShowEpisodeTitle = booleanPreferencesKey("show_episode_title")
        val LibrarySortMode = stringPreferencesKey("library_sort_mode")
        val LibraryFilters = stringPreferencesKey("library_filters")
        val ExperimentalDualBackendRace = booleanPreferencesKey("experimental_dual_backend_race")
        val HomeModuleOrder = stringPreferencesKey("home_module_order")
        val HomeModuleHidden = stringPreferencesKey("home_module_hidden")
        val LibraryColumnCount = intPreferencesKey("library_column_count")
    }
}
