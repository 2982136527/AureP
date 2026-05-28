package com.qiuhu.embyflow.model

import androidx.compose.ui.graphics.Color
import java.util.Locale

data class ChapterInfo(
    val name: String,
    val startPositionMs: Long,
)

data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Long,
    val baseUrl: String = "",
    val itemId: String = "",
) {
    val columns: Int get() = if (tileWidth > 0) width / tileWidth else 1
    val rows: Int get() = if (tileHeight > 0) height / tileHeight else 1
    val thumbnailsPerTile: Int get() = columns * rows

    fun tileUrl(index: Int): String {
        val tileIndex = if (thumbnailsPerTile > 0) index / thumbnailsPerTile else 0
        return "$baseUrl/Videos/$itemId/Trickplay/$width/$tileIndex.jpg"
    }

    fun thumbnailOffset(index: Int): Pair<Int, Int> {
        val localIndex = if (thumbnailsPerTile > 0) index % thumbnailsPerTile else 0
        val col = localIndex % columns
        val row = localIndex / columns
        return (col * tileWidth) to (row * tileHeight)
    }
}

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String,
    val summary: String,
    val score: String,
    val colors: List<Color>,
    val year: String = "",
    val genres: List<String> = emptyList(),
    val primaryImageAspectRatio: Double? = null,
    val primaryImageUrl: String? = null,
    val titleLogoUrl: String? = null,
    val seriesTitleLogoUrl: String? = null,
    val seriesPrimaryImageUrl: String? = null,
    val seriesBackdropImageUrl: String? = null,
    val backdropImageUrl: String? = null,
    val extraFanartUrls: List<String> = emptyList(),
    val actors: List<MediaPerson> = emptyList(),
    val mediaType: String = "",
    val collectionType: String = "",
    val seriesId: String? = null,
    val seriesName: String = "",
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val childCount: Int? = null,
    val unplayedItemCount: Int? = null,
    val isFolder: Boolean = false,
    val resumePositionMs: Long = 0L,
    val played: Boolean = false,
    val playedPercentage: Float = 0f,
    val chapters: List<ChapterInfo> = emptyList(),
    val trickplay: Map<Int, TrickplayInfo> = emptyMap(),
)

data class MediaPerson(
    val id: String,
    val name: String,
    val role: String = "",
    val imageUrl: String? = null,
)

data class MediaEdition(
    val id: String,
    val name: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null,
    val videoRange: String? = null,
    val bitDepth: Int? = null,
    val container: String? = null,
)

enum class MediaTagType {
    Genre,
    Year,
}

data class MediaTag(
    val label: String,
    val type: MediaTagType,
)

data class ServerSnapshot(
    val serverName: String,
    val serverVersion: String,
    val userName: String,
)

data class EmbyHomePayload(
    val server: ServerSnapshot,
    val heroItems: List<MediaItem>,
    val highlightItems: List<MediaItem>,
    val latestItems: List<MediaItem>,
    val resumeItems: List<MediaItem>,
    val nextUpItems: List<MediaItem> = emptyList(),
    val libraries: List<MediaItem>,
    val selectedLibraryId: String?,
    val libraryItems: List<MediaItem>,
    val libraryTotalCount: Int = 0,
)

fun placeholderColors(seed: String): List<Color> {
    val hash = seed.hashCode().toUInt().toLong()
    val hueA = (hash % 360).toFloat()
    val hueB = ((hash / 11) % 360).toFloat()
    return listOf(hslColor(hueA, 0.38f, 0.20f), hslColor(hueB, 0.42f, 0.34f))
}

fun MediaItem.detailTags(): List<MediaTag> {
    val tags = mutableListOf<MediaTag>()
    if (year.isNotBlank()) {
        tags += MediaTag(label = year, type = MediaTagType.Year)
    }
    genres
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .forEach { genre ->
            tags += MediaTag(label = genre, type = MediaTagType.Genre)
        }

    if (tags.isNotEmpty()) {
        return tags
    }

    return subtitle
        .split("  ")
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .map { label ->
            val type = if (label.all(Char::isDigit) && label.length == 4) MediaTagType.Year else MediaTagType.Genre
            MediaTag(label = label, type = type)
        }
}

val MediaItem.isSeries: Boolean
    get() = mediaType.equals("Series", ignoreCase = true)

val MediaItem.isSeason: Boolean
    get() = mediaType.equals("Season", ignoreCase = true)

val MediaItem.isEpisode: Boolean
    get() = mediaType.equals("Episode", ignoreCase = true)

fun MediaItem.seasonEpisodeLabel(): String {
    if (!isEpisode) return ""
    val season = seasonNumber?.coerceAtLeast(0)
    val episode = episodeNumber?.coerceAtLeast(0)
    return when {
        season != null && season > 0 && episode != null && episode > 0 -> "第${season}季 第${episode}集"
        episode != null && episode > 0 -> "第${episode}集"
        else -> ""
    }
}

fun MediaItem.cardEpisodeBadgeLabel(): String? {
    return when {
        isEpisode -> seasonEpisodeLabel().ifBlank { null }
        (isSeries || isSeason) && (childCount ?: 0) > 0 -> "${childCount}集"
        else -> null
    }
}

fun MediaItem.hasDisplayImage(): Boolean {
    return !primaryImageUrl.isNullOrBlank() || !backdropImageUrl.isNullOrBlank()
}

fun MediaItem.hasBackdropImage(): Boolean {
    val backdrop = backdropImageUrl?.takeIf { it.isNotBlank() } ?: return false
    return backdrop != primaryImageUrl
}

fun MediaItem.hasPosterImage(): Boolean {
    return !primaryImageUrl.isNullOrBlank()
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f

    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
    )
}

private fun sampleItem(
    id: String,
    title: String,
    subtitle: String,
    meta: String,
    summary: String,
    score: String,
): MediaItem = MediaItem(
    id = id,
    title = title,
    subtitle = subtitle,
    meta = meta,
    summary = summary,
    score = score,
    colors = placeholderColors(id),
)

object SampleCatalog {
    val heroItems = listOf(
        sampleItem(
            id = "rescue-plan",
            title = "援救计划",
            subtitle = "2026  科幻  冒险",
            meta = "4K  HEVC  Dolby Audio  双语字幕",
            summary = "一艘被遗弃的深空货轮突然重新向地球发送求救信号，三人回收小组在靠近后发现整艘船仍在运作。",
            score = "8.8",
        ),
        sampleItem(
            id = "night-shift",
            title = "夜班轨迹",
            subtitle = "2025  犯罪  剧情",
            meta = "1080P  H264  5.1  中文字幕",
            summary = "地铁调度员每晚都会收到来自废弃线路的匿名报站信息，而这些站点都对应着尚未发生的案件。",
            score = "8.4",
        ),
        sampleItem(
            id = "eclipse-coast",
            title = "蚀海",
            subtitle = "2024  悬疑  惊悚",
            meta = "4K  DV  Atmos  简繁字幕",
            summary = "海边观测站在日全食当天失去联系，一名声学工程师独自前往，发现整片海岸正在重复同一段潮汐。",
            score = "9.0",
        ),
    )

    val trendingItems = listOf(
        sampleItem(
            id = "trending-1",
            title = "明日奇谭",
            subtitle = "2024  新上线",
            meta = "HDR  字幕可用",
            summary = "为原型页准备的展示内容。",
            score = "8.1",
        ),
        sampleItem(
            id = "trending-2",
            title = "云图站台",
            subtitle = "2025  新上线",
            meta = "HDR  字幕可用",
            summary = "为原型页准备的展示内容。",
            score = "8.2",
        ),
        sampleItem(
            id = "trending-3",
            title = "无人潮线",
            subtitle = "2026  新上线",
            meta = "HDR  字幕可用",
            summary = "为原型页准备的展示内容。",
            score = "8.3",
        ),
    )

    val continueWatchingItems = listOf(
        sampleItem(
            id = "continue-1",
            title = "星港信号",
            subtitle = "看到 39:40",
            meta = "下一集可用",
            summary = "继续观看样例。",
            score = "8.2",
        ),
        sampleItem(
            id = "continue-2",
            title = "玻璃雨",
            subtitle = "看到 01:04:17",
            meta = "外挂字幕已匹配",
            summary = "继续观看样例。",
            score = "8.6",
        ),
    )

    val libraryItems = listOf(
        sampleItem(
            id = "library-1",
            title = "Aurora Files",
            subtitle = "2025",
            meta = "4K  HDR",
            summary = "媒体库样例条目。",
            score = "8.4",
        ),
        sampleItem(
            id = "library-2",
            title = "Crossfade City",
            subtitle = "2024",
            meta = "1080P  SDR",
            summary = "媒体库样例条目。",
            score = "8.2",
        ),
        sampleItem(
            id = "library-3",
            title = "Blue Transit",
            subtitle = "2023",
            meta = "4K  HDR",
            summary = "媒体库样例条目。",
            score = "8.5",
        ),
    )

    val fallbackPayload = EmbyHomePayload(
        server = ServerSnapshot(
            serverName = "EmbyFlow",
            serverVersion = "Prototype",
            userName = "Guest",
        ),
        heroItems = heroItems,
        highlightItems = trendingItems,
        latestItems = trendingItems,
        resumeItems = continueWatchingItems,
        nextUpItems = emptyList(),
        libraries = libraryItems.map {
            it.copy(
                isFolder = true,
                summary = "示例媒体库",
                meta = "Collection",
            )
        },
        selectedLibraryId = libraryItems.firstOrNull()?.id,
        libraryItems = libraryItems,
        libraryTotalCount = libraryItems.size,
    )

    fun findById(id: String): MediaItem? {
        return (
            fallbackPayload.heroItems +
                fallbackPayload.highlightItems +
                fallbackPayload.latestItems +
                fallbackPayload.resumeItems +
                fallbackPayload.libraries +
                fallbackPayload.libraryItems
            ).firstOrNull { it.id == id }
    }
}

fun formatRating(value: Double?): String =
    value?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
