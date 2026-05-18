package com.qiuhu.embyflow.data.resume

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.placeholderColors
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.continueWatchingDataStore: DataStore<Preferences> by preferencesDataStore(name = "continue_watching")

@Serializable
data class ContinueWatchingEntry(
    val id: String,
    val serverProfileId: String = "",
    val serverUserId: String = "",
    val title: String,
    val meta: String,
    val summary: String,
    val score: String,
    val year: String,
    val genres: List<String>,
    val primaryImageUrl: String? = null,
    val titleLogoUrl: String? = null,
    val seriesTitleLogoUrl: String? = null,
    val seriesPrimaryImageUrl: String? = null,
    val seriesBackdropImageUrl: String? = null,
    val backdropImageUrl: String? = null,
    val extraFanartUrls: List<String> = emptyList(),
    val seriesId: String? = null,
    val seriesName: String = "",
    val mediaType: String = "",
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isFolder: Boolean = false,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

class ContinueWatchingStore(
    context: Context,
) {
    private val dataStore = context.continueWatchingDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val entries: Flow<List<ContinueWatchingEntry>> = dataStore.data.map { preferences ->
        preferences[Keys.Entries]
            ?.let(::decodeEntries)
            .orEmpty()
            .normalizeEntries()
    }

    suspend fun update(
        media: MediaItem,
        positionMs: Long,
        durationMs: Long,
        serverProfileId: String,
        serverUserId: String,
    ) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.Entries]
                ?.let(::decodeEntries)
                .orEmpty()
                .normalizeEntries()
                .filterNot {
                    it.serverProfileId == serverProfileId &&
                    it.serverUserId == serverUserId &&
                        it.resumeGroupingKey() == media.resumeGroupingKey()
                }
                .toMutableList()

            if (shouldKeepResume(positionMs = positionMs, durationMs = durationMs, isFolder = media.isFolder)) {
                current += ContinueWatchingEntry(
                    id = media.id,
                    serverProfileId = serverProfileId,
                    serverUserId = serverUserId,
                    title = media.title,
                    meta = media.meta,
                    summary = media.summary,
                    score = media.score,
                    year = media.year,
                    genres = media.genres,
                    primaryImageUrl = media.primaryImageUrl,
                    titleLogoUrl = media.titleLogoUrl,
                    seriesTitleLogoUrl = media.seriesTitleLogoUrl,
                    seriesPrimaryImageUrl = media.seriesPrimaryImageUrl,
                    seriesBackdropImageUrl = media.seriesBackdropImageUrl,
                    backdropImageUrl = media.backdropImageUrl,
                    extraFanartUrls = media.extraFanartUrls,
                    seriesId = media.seriesId,
                    seriesName = media.seriesName,
                    mediaType = media.mediaType,
                    seasonId = media.seasonId,
                    seasonNumber = media.seasonNumber,
                    episodeNumber = media.episodeNumber,
                    isFolder = media.isFolder,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                )
            }

            preferences[Keys.Entries] = json.encodeToString(
                current
                    .normalizeEntries()
                    .take(72),
            )
        }
    }

    private fun decodeEntries(serialized: String): List<ContinueWatchingEntry> = runCatching {
        json.decodeFromString<List<ContinueWatchingEntry>>(serialized)
    }.getOrDefault(emptyList())

    private object Keys {
        val Entries = stringPreferencesKey("entries")
    }
}

private fun List<ContinueWatchingEntry>.normalizeEntries(): List<ContinueWatchingEntry> {
    val items = LinkedHashMap<String, ContinueWatchingEntry>()
    for (entry in sortedByDescending { it.updatedAt }) {
        val key = entry.scopedResumeGroupingKey()
        if (!items.containsKey(key)) {
            items[key] = entry
        }
    }
    return items.values.toList()
}

private fun ContinueWatchingEntry.scopedResumeGroupingKey(): String = buildString {
    append(serverProfileId)
    append('|')
    append(serverUserId)
    append('|')
    append(resumeGroupingKey())
}

private fun MediaItem.resumeGroupingKey(): String = when {
    !seriesId.isNullOrBlank() ->
        "series:${seriesId?.takeIf { it.isNotBlank() } ?: seriesName.takeIf { it.isNotBlank() } ?: id}"
    mediaType.equals("Episode", ignoreCase = true) ->
        "series:${seriesName.takeIf { it.isNotBlank() } ?: id}"
    mediaType.equals("Series", ignoreCase = true) -> "series:$id"
    else -> "item:$id"
}

private fun ContinueWatchingEntry.resumeGroupingKey(): String = when {
    !seriesId.isNullOrBlank() ->
        "series:${seriesId?.takeIf { it.isNotBlank() } ?: seriesName.takeIf { it.isNotBlank() } ?: id}"
    mediaType.equals("Episode", ignoreCase = true) ->
        "series:${seriesName.takeIf { it.isNotBlank() } ?: id}"
    mediaType.equals("Series", ignoreCase = true) -> "series:$id"
    else -> "item:$id"
}

fun ContinueWatchingEntry.toMediaItem(): MediaItem = MediaItem(
    id = id,
    title = title,
    subtitle = "看到 ${formatPlaybackTime(positionMs)}",
    meta = meta,
    summary = summary,
    score = score,
    colors = placeholderColors(id),
    year = year,
    genres = genres,
    primaryImageUrl = primaryImageUrl,
    titleLogoUrl = titleLogoUrl,
    seriesTitleLogoUrl = seriesTitleLogoUrl,
    seriesPrimaryImageUrl = seriesPrimaryImageUrl,
    seriesBackdropImageUrl = seriesBackdropImageUrl,
    backdropImageUrl = backdropImageUrl,
    extraFanartUrls = extraFanartUrls,
    seriesId = seriesId,
    seriesName = seriesName,
    mediaType = mediaType,
    seasonId = seasonId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    isFolder = isFolder,
    resumePositionMs = positionMs,
)

private fun shouldKeepResume(
    positionMs: Long,
    durationMs: Long,
    isFolder: Boolean,
): Boolean {
    if (isFolder || durationMs <= 0L) return false
    if (positionMs < 15_000L) return false
    val progress = positionMs / durationMs.toFloat()
    val remainingMs = durationMs - positionMs
    return progress < 0.96f && remainingMs > 30_000L
}

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
