package com.qiuhu.embyflow.data.emby

import android.net.Uri
import android.util.Log
import com.qiuhu.embyflow.BuildConfig
import com.qiuhu.embyflow.data.settings.librarySortSpec
import com.qiuhu.embyflow.model.EmbyHomePayload
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.MediaPerson
import com.qiuhu.embyflow.model.MediaTag
import com.qiuhu.embyflow.model.MediaTagType
import com.qiuhu.embyflow.model.ServerSnapshot
import com.qiuhu.embyflow.model.hasBackdropImage
import com.qiuhu.embyflow.model.hasPosterImage
import com.qiuhu.embyflow.model.formatRating
import com.qiuhu.embyflow.model.placeholderColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

data class EmbySession(
    val serverName: String,
    val serverVersion: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
)

data class EmbyBootstrapResult(
    val session: EmbySession,
    val payload: EmbyHomePayload,
)

data class EmbyPagedMediaItems(
    val items: List<MediaItem>,
    val totalCount: Int,
)

data class EmbySeriesContent(
    val seasons: List<MediaItem>,
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItem>,
    val nextUpEpisode: MediaItem? = null,
)

data class EmbyPlaybackSource(
    val streamUrl: String,
    val title: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val infoLine: String = "",
    val infoFields: List<EmbyPlaybackInfoField> = emptyList(),
    val subtitleTracks: List<EmbySubtitleTrack> = emptyList(),
    val streamOptions: List<EmbyPlaybackStreamOption> = emptyList(),
    val selectedStreamOptionId: String? = null,
)

data class EmbyPlaybackSessionState(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPaused: Boolean,
    val playbackRate: Double,
    val subtitleStreamIndex: Int? = null,
    val audioStreamIndex: Int? = null,
    val playMethod: String,
    val canSeek: Boolean = true,
    val isMuted: Boolean = false,
    val volumeLevel: Int? = null,
)

data class EmbyPlaybackInfoField(
    val label: String,
    val value: String,
)

data class EmbyPlaybackStreamOption(
    val id: String,
    val label: String,
    val description: String,
    val streamUrl: String,
    val lockedByServer: Boolean = false,
    val requestHeaders: Map<String, String> = emptyMap(),
)

data class EmbySubtitleTrack(
    val index: Int,
    val label: String,
    val language: String?,
    val url: String,
    val mimeType: String,
    val isDefault: Boolean,
    val isExternal: Boolean,
)

@Serializable
private data class PlaybackInfoRequestDto(
    val Id: String,
    val UserId: String,
    val MaxStreamingBitrate: Long,
    val MaxAudioChannels: Int? = null,
    val EnableDirectPlay: Boolean = true,
    val EnableDirectStream: Boolean = true,
    val EnableTranscoding: Boolean = true,
    val AllowVideoStreamCopy: Boolean = true,
    val AllowAudioStreamCopy: Boolean = true,
    val AutoOpenLiveStream: Boolean = false,
    val IsPlayback: Boolean = true,
    val DeviceProfile: PlaybackDeviceProfileDto? = null,
)

@Serializable
private data class PlaybackDeviceProfileDto(
    val Name: String,
    val Id: String,
    val SupportedMediaTypes: String,
    val MaxStreamingBitrate: Long,
    val MaxStaticBitrate: Long,
    val MusicStreamingTranscodingBitrate: Int,
    val MaxStaticMusicBitrate: Int,
    val DirectPlayProfiles: List<PlaybackDirectPlayProfileDto>,
    val TranscodingProfiles: List<PlaybackTranscodingProfileDto>,
    val CodecProfiles: List<PlaybackCodecProfileDto>,
    val SubtitleProfiles: List<PlaybackSubtitleProfileDto>,
)

@Serializable
private data class PlaybackDirectPlayProfileDto(
    val Type: String,
    val Container: String? = null,
    val AudioCodec: String? = null,
    val VideoCodec: String? = null,
)

@Serializable
private data class PlaybackTranscodingProfileDto(
    val Container: String,
    val Type: String,
    val Protocol: String,
    val Context: String,
    val AudioCodec: String? = null,
    val VideoCodec: String? = null,
    val EstimateContentLength: Boolean = false,
    val EnableMpegtsM2TsMode: Boolean = false,
    val TranscodeSeekInfo: String = "Auto",
    val CopyTimestamps: Boolean = false,
    val BreakOnNonKeyFrames: Boolean = false,
    val AllowInterlacedVideoStreamCopy: Boolean = false,
    val MaxAudioChannels: String? = null,
    val SegmentLength: Int = 3,
    val MinSegments: Int = 1,
)

@Serializable
private data class PlaybackCodecProfileDto(
    val Type: String,
    val Codec: String,
    val Conditions: List<PlaybackProfileConditionDto> = emptyList(),
)

@Serializable
private data class PlaybackProfileConditionDto(
    val Condition: String,
    val Property: String,
    val Value: String,
    val IsRequired: Boolean = false,
)

@Serializable
private data class PlaybackSubtitleProfileDto(
    val Format: String,
    val Method: String,
)

@Serializable
private data class PlaybackCheckInDto(
    val QueueableMediaTypes: List<String> = listOf("Video"),
    val CanSeek: Boolean = true,
    val ItemId: String,
    val MediaSourceId: String,
    val PositionTicks: Long? = null,
    val RunTimeTicks: Long? = null,
    val AudioStreamIndex: Int? = null,
    val SubtitleStreamIndex: Int? = null,
    val IsPaused: Boolean,
    val IsMuted: Boolean = false,
    val VolumeLevel: Int? = null,
    val PlayMethod: String,
    val PlaySessionId: String,
    val SessionId: String? = null,
    val LiveStreamId: String? = null,
    val PlaylistIndex: Int = 0,
    val PlaylistLength: Int = 1,
    val SubtitleOffset: Int = 0,
    val PlaybackRate: Double? = null,
    val EventName: String? = null,
    val PlaybackStartTimeTicks: Long? = null,
)

class EmbyRepository(
    private val serverUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val LibraryPageSize = 24
        private const val PlaybackDebugTag = "EmbyFlowPlayback"
        private const val PLAY_SOURCE_QUERY_PARAM = "play_source"
        private const val PLAY_SOURCE_EMBY_PROXY = "emby_proxy"
        private const val PlaybackDeviceProfileName = "AureP-Android"
        private const val PlaybackMaxStreamingBitrate = 20_000_000L
    }

    @Volatile
    private var baseUrl = serverUrl.trimEnd('/')
    private val client = OkHttpClient.Builder().build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val authorizationHeader =
        "MediaBrowser Client=\"AureP\", Device=\"Android\", DeviceId=\"aurep-android\", Version=\"${BuildConfig.VERSION_NAME}\""
    private val browseFields =
        "PrimaryImageAspectRatio,Overview,CommunityRating,PremiereDate,Genres,RunTimeTicks,BackdropImageTags,SeriesName,SeriesId,SeasonId,IndexNumber,ParentIndexNumber,ProductionYear,OfficialRating,RecursiveItemCount,ChildCount,ParentId"
    private val detailFields = "People,$browseFields"

    suspend fun bootstrap(
        username: String,
        password: String,
        librarySortMode: String,
    ): EmbyBootstrapResult = withContext(Dispatchers.IO) {
        val publicInfo = get<PublicSystemInfoDto>("/System/Info/Public")
        val authResponse = authenticate(username = username, password = password)
        val session = EmbySession(
            serverName = publicInfo.ServerName,
            serverVersion = publicInfo.Version,
            userId = authResponse.User.Id,
            userName = authResponse.User.Name,
            accessToken = authResponse.AccessToken,
        )

        val views = get<QueryResultDto>(
            path = "/Users/${session.userId}/Views?Fields=PrimaryImageAspectRatio",
            token = session.accessToken,
        )
        val resume = get<QueryResultDto>(
            path = "/Users/${session.userId}/Items/Resume?Limit=12&Fields=${browseFields.urlEncode()}",
            token = session.accessToken,
        )
        val latest = getList<BaseItemDto>(
            path = "/Users/${session.userId}/Items/Latest?Limit=18&Fields=${browseFields.urlEncode()}",
            token = session.accessToken,
        )
        val heroItems = loadRandomHeroItems(
            userId = session.userId,
            token = session.accessToken,
        )
        val highlightItems = loadHighlightItems(
            userId = session.userId,
            token = session.accessToken,
        )

        val libraries = views.Items.map { item -> item.toMediaItem(token = session.accessToken) }
        val selectedLibraryId = libraries.firstOrNull()?.id
        val libraryPage = if (selectedLibraryId != null) {
            loadLibraryItems(
                userId = session.userId,
                token = session.accessToken,
                parentId = selectedLibraryId,
                collectionType = libraries.firstOrNull()?.collectionType.orEmpty(),
                sortMode = librarySortMode,
            )
        } else {
            EmbyPagedMediaItems(
                items = emptyList(),
                totalCount = 0,
            )
        }

        EmbyBootstrapResult(
            session = session,
            payload = EmbyHomePayload(
                server = ServerSnapshot(
                    serverName = session.serverName,
                    serverVersion = session.serverVersion,
                    userName = session.userName,
                ),
                heroItems = heroItems,
                highlightItems = highlightItems,
                latestItems = latest.map { it.toMediaItem(session.accessToken) },
                resumeItems = resume.Items.map { it.toMediaItem(session.accessToken) },
                libraries = libraries,
                selectedLibraryId = selectedLibraryId,
                libraryItems = libraryPage.items,
                libraryTotalCount = libraryPage.totalCount,
            ),
        )
    }

    private suspend fun loadRandomHeroItems(
        userId: String,
        token: String,
        limit: Int = 24,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val query = listOf(
            "Recursive" to "true",
            "SortBy" to "Random",
            "Limit" to limit.toString(),
            "IncludeItemTypes" to "Movie,Series,Video",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items
            .map { it.toMediaItem(token = token) }
            .filter { it.hasBackdropImage() }
            .distinctBy { it.id }
    }

    private suspend fun loadHighlightItems(
        userId: String,
        token: String,
        limit: Int = 240,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val query = listOf(
            "Recursive" to "true",
            "SortBy" to "DateCreated,SortName",
            "SortOrder" to "Descending",
            "Limit" to limit.toString(),
            "IncludeItemTypes" to "Movie,Series,Video",
            "Fields" to "DateCreated,$browseFields",
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        val candidates = get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items
            .mapNotNull { item ->
                val media = item.toMediaItem(token = token)
                val createdAt = item.parseDateCreated()
                media
                    .takeIf { it.hasPosterImage() }
                    ?.let { it to createdAt }
            }
            .distinctBy { it.first.id }

        if (candidates.isEmpty()) {
            return@withContext emptyList()
        }

        var windowDays = 7L
        while (windowDays <= 365L) {
            val threshold = Instant.now().minus(windowDays, ChronoUnit.DAYS)
            val itemsInWindow = candidates
                .filter { (_, createdAt) -> createdAt != null && !createdAt.isBefore(threshold) }
                .map { it.first }
            if (itemsInWindow.isNotEmpty()) {
                return@withContext itemsInWindow
            }
            windowDays += 7L
        }

        return@withContext candidates.map { it.first }
    }

    suspend fun loadLibraryItems(
        userId: String,
        token: String,
        parentId: String,
        collectionType: String,
        sortMode: String,
        startIndex: Int = 0,
        limit: Int = LibraryPageSize,
    ): EmbyPagedMediaItems = withContext(Dispatchers.IO) {
        val sortSpec = librarySortSpec(sortMode)
        val normalizedCollectionType = collectionType.lowercase(Locale.US)
        val includeItemTypes = when (normalizedCollectionType) {
            "tvshows" -> "Series"
            else -> "Movie,Episode,Series,Video"
        }
        val query = listOf(
            "ParentId" to parentId,
            "Recursive" to "true",
            "SortBy" to sortSpec.sortBy,
            "SortOrder" to sortSpec.sortOrder,
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
            "IncludeItemTypes" to includeItemTypes,
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        val result = get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        )
        val items = result.Items
            .map { it.toMediaItem(token = token) }
            .filterNot { media ->
                normalizedCollectionType == "tvshows" && media.isFolder
            }
        EmbyPagedMediaItems(
            items = items,
            totalCount = result.TotalRecordCount,
        )
    }

    suspend fun loadItemsForTag(
        userId: String,
        token: String,
        tag: MediaTag,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val tagParam = when (tag.type) {
            MediaTagType.Genre -> "Genres" to tag.label
            MediaTagType.Year -> "Years" to tag.label
        }

        val query = listOf(
            tagParam,
            "Recursive" to "true",
            "SortBy" to "DateCreated,SortName",
            "SortOrder" to "Descending",
            "Limit" to "60",
            "IncludeItemTypes" to "Movie,Series,Video",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items.map { it.toMediaItem(token = token) }
    }

    suspend fun loadItemsForPerson(
        userId: String,
        token: String,
        personId: String,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val normalizedPersonId = personId.trim()
        if (normalizedPersonId.isBlank()) {
            return@withContext emptyList()
        }

        val query = listOf(
            "PersonIds" to normalizedPersonId,
            "Recursive" to "true",
            "SortBy" to "DateCreated,SortName",
            "SortOrder" to "Descending",
            "Limit" to "60",
            "IncludeItemTypes" to "Movie,Series,Episode,Video",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items.map { it.toMediaItem(token = token) }
    }

    suspend fun searchMedia(
        userId: String,
        token: String,
        query: String,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return@withContext emptyList()
        }

        val requestQuery = listOf(
            "Recursive" to "true",
            "SearchTerm" to normalized,
            "Limit" to "40",
            "IncludeItemTypes" to "Movie,Episode,Series,Video",
            "Fields" to detailFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        get<QueryResultDto>(
            path = "/Users/$userId/Items?$requestQuery",
            token = token,
        ).Items
            .map { it.toMediaItem(token = token) }
            .distinctBy { it.id }
    }

    suspend fun loadMediaDetail(
        userId: String,
        token: String,
        itemId: String,
    ): MediaItem = withContext(Dispatchers.IO) {
        val query = listOf(
            "Fields" to detailFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        get<BaseItemDto>(
            path = "/Users/$userId/Items/${itemId.urlEncode()}?$query",
            token = token,
        ).toMediaItem(token = token)
    }

    suspend fun loadSeriesContent(
        userId: String,
        token: String,
        seriesId: String,
    ): EmbySeriesContent = withContext(Dispatchers.IO) {
        val seasons = loadSeasons(
            userId = userId,
            token = token,
            seriesId = seriesId,
        )
        val nextUpEpisode = loadNextUpEpisode(
            userId = userId,
            token = token,
            seriesId = seriesId,
        )
        val selectedSeasonId = nextUpEpisode?.seasonId?.takeIf { seasonId ->
            seasons.any { it.id == seasonId }
        } ?: seasons.firstOrNull()?.id
        val episodes = when {
            selectedSeasonId != null -> loadSeasonEpisodes(
                userId = userId,
                token = token,
                seasonId = selectedSeasonId,
            )
            else -> loadDirectEpisodesForSeries(
                userId = userId,
                token = token,
                seriesId = seriesId,
            )
        }

        EmbySeriesContent(
            seasons = seasons,
            selectedSeasonId = selectedSeasonId,
            episodes = episodes,
            nextUpEpisode = nextUpEpisode,
        )
    }

    suspend fun loadSeasonEpisodes(
        userId: String,
        token: String,
        seasonId: String,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        loadEpisodesForParent(
            userId = userId,
            token = token,
            parentId = seasonId,
        )
    }

    suspend fun resolvePlayableItem(
        userId: String,
        token: String,
        media: MediaItem,
    ): MediaItem = withContext(Dispatchers.IO) {
        when {
            media.mediaType.equals("Series", ignoreCase = true) -> {
                loadNextUpEpisode(
                    userId = userId,
                    token = token,
                    seriesId = media.id,
                ) ?: loadFirstEpisodeForSeries(
                    userId = userId,
                    token = token,
                    seriesId = media.id,
                ) ?: throw IllegalStateException("这个剧集还没有可播放的分集")
            }

            media.mediaType.equals("Season", ignoreCase = true) -> {
                loadEpisodesForParent(
                    userId = userId,
                    token = token,
                    parentId = media.id,
                ).firstOrNull() ?: throw IllegalStateException("这个分季下还没有可播放的分集")
            }

            else -> media
        }
    }

    suspend fun loadPlaybackSource(
        userId: String,
        token: String,
        itemId: String,
        fallbackTitle: String,
    ): EmbyPlaybackSource = withContext(Dispatchers.IO) {
        val playbackInfo = post<PlaybackInfoDto>(
            path = "/Items/$itemId/PlaybackInfo?UserId=${userId.urlEncode()}",
            token = token,
            body = buildPlaybackInfoRequestBody(
                userId = userId,
                itemId = itemId,
            ),
        )
        val source = playbackInfo.MediaSources.firstOrNull()
            ?: throw IllegalStateException("服务端没有返回可播放媒体源")
        val subtitleTracks = source.buildSubtitleTracks(
            itemId = itemId,
            baseUrl = baseUrl,
            token = token,
        )
        val streamOptions = source.buildStreamOptions(
            itemId = itemId,
            baseUrl = baseUrl,
            token = token,
            playSessionId = playbackInfo.PlaySessionId,
        )
        val selectedStreamOption = streamOptions.firstOrNull()
            ?: throw IllegalStateException("服务端没有返回可用播放链路")
        val primaryVideoStream = source.MediaStreams.firstOrNull { it.Type.equals("Video", ignoreCase = true) }
        val primaryAudioStream = source.MediaStreams.firstOrNull { it.Type.equals("Audio", ignoreCase = true) }
        Log.i(
            PlaybackDebugTag,
            buildString {
                append("title=")
                append(source.Name ?: fallbackTitle)
                append(" itemId=")
                append(itemId)
                append(" container=")
                append(source.Container.orEmpty())
                append(" video=")
                append(primaryVideoStream?.Codec.orEmpty())
                append('/')
                append(primaryVideoStream?.Profile.orEmpty())
                append('/')
                append(primaryVideoStream?.BitDepth ?: 0)
                append("bit")
                append(" audio=")
                append(primaryAudioStream?.Codec.orEmpty())
                append('/')
                append(primaryAudioStream?.Channels ?: 0)
                append("ch")
                append(" mediaSourceId=")
                append(source.Id)
                append(" path=")
                append(source.Path)
                append(" directStreamUrl=")
                append(source.DirectStreamUrl.orEmpty())
                append(" transcodingUrl=")
                append(source.TranscodingUrl.orEmpty())
                append(" isRemote=")
                append(source.IsRemote)
                append(" supportsDirectStream=")
                append(source.SupportsDirectStream)
                append(" supportsTranscoding=")
                append(source.SupportsTranscoding)
                append(" selected=")
                append(selectedStreamOption.id)
                append(" -> ")
                append(selectedStreamOption.streamUrl)
                append(" options=")
                append(
                    streamOptions.joinToString(separator = " | ") { option ->
                        "${option.id}=${option.streamUrl}"
                    },
                )
            },
        )

        EmbyPlaybackSource(
            streamUrl = selectedStreamOption.streamUrl,
            title = source.Name ?: fallbackTitle,
            mediaSourceId = source.Id,
            playSessionId = playbackInfo.PlaySessionId,
            infoLine = source.buildInfoLine(
                subtitleCount = subtitleTracks.size,
            ),
            infoFields = source.buildInfoFields(
                subtitleTracks = subtitleTracks,
            ),
            subtitleTracks = subtitleTracks,
            streamOptions = streamOptions,
            selectedStreamOptionId = selectedStreamOption.id,
        )
    }

    private fun buildPlaybackInfoRequestBody(
        userId: String,
        itemId: String,
    ): String {
        val request = PlaybackInfoRequestDto(
            Id = itemId,
            UserId = userId,
            MaxStreamingBitrate = PlaybackMaxStreamingBitrate,
            MaxAudioChannels = 6,
            AllowVideoStreamCopy = true,
            AllowAudioStreamCopy = true,
        )
        return json.encodeToString(request)
    }

    suspend fun reportPlaybackStarted(
        userId: String,
        token: String,
        state: EmbyPlaybackSessionState,
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || token.isBlank()) return@withContext
        postEmpty(
            path = "/Sessions/Playing",
            token = token,
            body = json.encodeToString(
                buildPlaybackCheckInDto(
                    state = state,
                    eventName = null,
                    includePlaybackStartTimeTicks = true,
                ),
            ),
        )
    }

    suspend fun reportPlaybackProgress(
        userId: String,
        token: String,
        state: EmbyPlaybackSessionState,
        eventName: String,
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || token.isBlank()) return@withContext
        postEmpty(
            path = "/Sessions/Playing/Progress",
            token = token,
            body = json.encodeToString(
                buildPlaybackCheckInDto(
                    state = state,
                    eventName = eventName,
                    includePlaybackStartTimeTicks = false,
                ),
            ),
        )
    }

    suspend fun reportPlaybackStopped(
        userId: String,
        token: String,
        state: EmbyPlaybackSessionState,
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || token.isBlank()) return@withContext
        postEmpty(
            path = "/Sessions/Playing/Stopped",
            token = token,
            body = json.encodeToString(
                buildPlaybackCheckInDto(
                    state = state,
                    eventName = null,
                    includePlaybackStartTimeTicks = false,
                ),
            ),
        )
    }

    private fun buildPlaybackCheckInDto(
        state: EmbyPlaybackSessionState,
        eventName: String?,
        includePlaybackStartTimeTicks: Boolean,
    ): PlaybackCheckInDto {
        return PlaybackCheckInDto(
            CanSeek = state.canSeek,
            ItemId = state.itemId,
            MediaSourceId = state.mediaSourceId,
            PositionTicks = state.positionMs.takeIf { it > 0L }?.msToTicks(),
            RunTimeTicks = state.durationMs.takeIf { it > 0L }?.msToTicks(),
            AudioStreamIndex = state.audioStreamIndex,
            SubtitleStreamIndex = state.subtitleStreamIndex,
            IsPaused = state.isPaused,
            IsMuted = state.isMuted,
            VolumeLevel = state.volumeLevel,
            PlayMethod = state.playMethod,
            PlaySessionId = state.playSessionId,
            SessionId = state.playSessionId,
            PlaybackRate = state.playbackRate,
            EventName = eventName,
            PlaybackStartTimeTicks = if (includePlaybackStartTimeTicks) currentDateTimeTicks() else null,
        )
    }

    private fun loadSeasons(
        userId: String,
        token: String,
        seriesId: String,
    ): List<MediaItem> {
        val query = listOf(
            "ParentId" to seriesId,
            "IncludeItemTypes" to "Season",
            "Recursive" to "true",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        return get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items
            .map { it.toMediaItem(token = token) }
            .sortedWith(
                compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.US) },
            )
    }

    private fun loadNextUpEpisode(
        userId: String,
        token: String,
        seriesId: String,
    ): MediaItem? {
        val query = listOf(
            "UserId" to userId,
            "SeriesId" to seriesId,
            "Limit" to "1",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        return get<QueryResultDto>(
            path = "/Shows/NextUp?$query",
            token = token,
        ).Items
            .firstOrNull()
            ?.toMediaItem(token = token)
    }

    private fun loadFirstEpisodeForSeries(
        userId: String,
        token: String,
        seriesId: String,
    ): MediaItem? {
        val firstSeasonId = loadSeasons(
            userId = userId,
            token = token,
            seriesId = seriesId,
        ).firstOrNull()?.id

        return if (firstSeasonId != null) {
            loadEpisodesForParent(
                userId = userId,
                token = token,
                parentId = firstSeasonId,
            ).firstOrNull()
        } else {
            loadDirectEpisodesForSeries(
                userId = userId,
                token = token,
                seriesId = seriesId,
            ).firstOrNull()
        }
    }

    private fun loadDirectEpisodesForSeries(
        userId: String,
        token: String,
        seriesId: String,
    ): List<MediaItem> {
        return loadEpisodesForParent(
            userId = userId,
            token = token,
            parentId = seriesId,
        )
    }

    private fun loadEpisodesForParent(
        userId: String,
        token: String,
        parentId: String,
    ): List<MediaItem> {
        val query = listOf(
            "ParentId" to parentId,
            "IncludeItemTypes" to "Episode",
            "Recursive" to "true",
            "Limit" to "200",
            "Fields" to browseFields,
        ).joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }

        return get<QueryResultDto>(
            path = "/Users/$userId/Items?$query",
            token = token,
        ).Items
            .map { it.toMediaItem(token = token) }
            .sortedWith(
                compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }
                    .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.US) },
            )
    }

    private fun authenticate(
        username: String,
        password: String,
    ): AuthResponseDto {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }
        return post(
            path = "/Users/AuthenticateByName",
            body = json.encodeToString(body),
        )
    }

    private inline fun <reified T> get(
        path: String,
        token: String? = null,
    ): T {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept-Encoding", "identity")
            .header("X-Emby-Authorization", authorizationHeader)
            .apply {
                if (token != null) {
                    header("X-Emby-Token", token)
                }
            }
            .build()

        return client.newCall(request).execute().use { response ->
            updateBaseUrlFromResponse(
                finalUrl = response.request.url,
                requestedPath = path,
            )
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $responseBody")
            }
            json.decodeFromString(responseBody)
        }
    }

    private inline fun <reified T> getList(
        path: String,
        token: String,
    ): List<T> {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept-Encoding", "identity")
            .header("X-Emby-Authorization", authorizationHeader)
            .header("X-Emby-Token", token)
            .build()

        return client.newCall(request).execute().use { response ->
            updateBaseUrlFromResponse(
                finalUrl = response.request.url,
                requestedPath = path,
            )
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $responseBody")
            }
            json.decodeFromString(responseBody)
        }
    }

    private inline fun <reified T> post(
        path: String,
        token: String? = null,
        body: String,
    ): T {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept-Encoding", "identity")
            .header("Content-Type", "application/json")
            .header("X-Emby-Authorization", authorizationHeader)
            .apply {
                if (token != null) {
                    header("X-Emby-Token", token)
                }
            }
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return client.newCall(request).execute().use { response ->
            updateBaseUrlFromResponse(
                finalUrl = response.request.url,
                requestedPath = path,
            )
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $responseBody")
            }
            json.decodeFromString(responseBody)
        }
    }

    private fun postEmpty(
        path: String,
        token: String? = null,
        body: String,
    ) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept-Encoding", "identity")
            .header("Content-Type", "application/json")
            .header("X-Emby-Authorization", authorizationHeader)
            .apply {
                if (token != null) {
                    header("X-Emby-Token", token)
                }
            }
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            updateBaseUrlFromResponse(
                finalUrl = response.request.url,
                requestedPath = path,
            )
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $responseBody")
            }
        }
    }

    private fun updateBaseUrlFromResponse(
        finalUrl: HttpUrl,
        requestedPath: String,
    ) {
        val pathOnly = requestedPath.substringBefore('?')
        val finalPath = finalUrl.encodedPath
        val basePath = finalPath
            .takeIf { it.endsWith(pathOnly) }
            ?.removeSuffix(pathOnly)
            ?.trimEnd('/')
            .orEmpty()

        baseUrl = buildString {
            append(finalUrl.scheme)
            append("://")
            append(finalUrl.host)
            if (!finalUrl.isDefaultPort()) {
                append(':')
                append(finalUrl.port)
            }
            append(basePath)
        }
    }

    private fun HttpUrl.isDefaultPort(): Boolean = when (scheme) {
        "http" -> port == 80
        "https" -> port == 443
        else -> false
    }

    private fun BaseItemDto.toMediaItem(token: String): MediaItem {
        val year = ProductionYear?.toString().orEmpty().ifBlank { PremiereDate?.take(4).orEmpty() }
        val primaryGenre = Genres.firstOrNull().orEmpty()
        val seasonEpisodeLabel = buildSeasonEpisodeLabel(
            seasonNumber = ParentIndexNumber,
            episodeNumber = IndexNumber,
        )
        val subtitle = when {
            UserData?.PlaybackPositionTicks ?: 0 > 0 -> "看到 ${formatPlaybackPosition(UserData?.PlaybackPositionTicks ?: 0)}"
            Type == "Episode" && seasonEpisodeLabel.isNotBlank() && !SeriesName.isNullOrBlank() ->
                "$seasonEpisodeLabel  ${SeriesName}"
            Type == "Episode" && seasonEpisodeLabel.isNotBlank() -> seasonEpisodeLabel
            Type == "Season" && IndexNumber != null -> "第${IndexNumber}季"
            Type == "Series" && year.isNotBlank() && (ChildCount ?: 0) > 0 -> "$year  ${ChildCount}季"
            year.isNotBlank() && primaryGenre.isNotBlank() -> "$year  $primaryGenre"
            year.isNotBlank() -> year
            primaryGenre.isNotBlank() -> primaryGenre
            IsFolder -> "媒体库"
            else -> typeLabel(Type)
        }
        val meta = buildString {
            when (Type) {
                "Episode" -> {
                    if (!SeriesName.isNullOrBlank()) {
                        append(SeriesName)
                    }
                    formatRuntime(RunTimeTicks).takeIf { it.isNotBlank() }?.let { runtime ->
                        if (isNotEmpty()) append("  ")
                        append(runtime)
                    }
                }
                "Series" -> {
                    OfficialRating
                        ?.takeIf { it.isNotBlank() }
                        ?.let { rating ->
                            append(rating)
                        }
                    UserData?.UnplayedItemCount
                        ?.takeIf { it > 0 }
                        ?.let { count ->
                            if (isNotEmpty()) append("  ")
                            append("待看${count}集")
                        }
                    if (isEmpty() && primaryGenre.isNotBlank()) {
                        append(primaryGenre)
                    }
                }
            }

            if (CommunityRating != null) {
                if (isNotEmpty()) append("  ")
                append("评分 ${formatRating(CommunityRating)}")
            }
            val runtimeLabel = formatRuntime(RunTimeTicks)
            if (runtimeLabel.isNotBlank() && Type != "Episode") {
                if (isNotEmpty()) append("  ")
                append(runtimeLabel)
            }
            if (isEmpty()) {
                append(typeLabel(Type))
            }
        }
        val primaryImageUrl = buildPrimaryImageUrl(token)
        val titleLogoUrl = buildLogoImageUrl(token)
        val extraFanartUrls = buildBackdropImageUrls(token)
        val backdropImageUrl = extraFanartUrls.firstOrNull()

        return MediaItem(
            id = Id,
            title = Name,
            subtitle = subtitle,
            meta = meta,
            summary = Overview?.takeIf { it.isNotBlank() }
                ?: if (IsFolder) "${ChildCount ?: 0} 项内容" else "暂无简介",
            score = formatRating(CommunityRating),
            colors = placeholderColors(Id),
            year = year,
            genres = Genres,
            primaryImageAspectRatio = PrimaryImageAspectRatio,
            primaryImageUrl = primaryImageUrl,
            titleLogoUrl = titleLogoUrl,
            backdropImageUrl = backdropImageUrl,
            extraFanartUrls = extraFanartUrls,
            actors = buildActors(token),
            mediaType = Type.orEmpty(),
            collectionType = CollectionType.orEmpty(),
            seriesId = SeriesId,
            seriesName = SeriesName.orEmpty(),
            seasonId = SeasonId,
            seasonNumber = when (Type) {
                "Season" -> IndexNumber
                "Episode" -> ParentIndexNumber
                else -> null
            },
            episodeNumber = IndexNumber?.takeIf { Type == "Episode" },
            childCount = RecursiveItemCount ?: ChildCount,
            unplayedItemCount = UserData?.UnplayedItemCount,
            isFolder = IsFolder,
            resumePositionMs = ((UserData?.PlaybackPositionTicks ?: 0L) / 10_000L).coerceAtLeast(0L),
        )
    }

    private fun BaseItemDto.buildActors(token: String): List<MediaPerson> {
        return People.asSequence()
            .filter { it.Type.equals("Actor", ignoreCase = true) }
            .map { person ->
                MediaPerson(
                    id = person.Id,
                    name = person.Name.ifBlank { "未知演员" },
                    role = person.Role.orEmpty(),
                    imageUrl = person.buildPrimaryImageUrl(token),
                )
            }
            .distinctBy { it.id.ifBlank { it.name } }
            .toList()
    }

    private fun PersonDto.buildPrimaryImageUrl(token: String): String? {
        val personId = Id.takeIf { it.isNotBlank() } ?: return null
        val tag = PrimaryImageTag?.takeIf { it.isNotBlank() }
        return buildString {
            append(baseUrl)
            append("/Items/")
            append(personId.urlEncode())
            append("/Images/Primary?maxWidth=360&quality=90")
            if (tag != null) {
                append("&tag=")
                append(tag.urlEncode())
            }
            append("&X-Emby-Token=")
            append(token.urlEncode())
        }
    }

    private fun BaseItemDto.buildPrimaryImageUrl(token: String): String? {
        val tag = ImageTags?.Primary ?: ImageTags?.Thumb ?: return null
        val imageType = if (ImageTags?.Primary != null) "Primary" else "Thumb"
        return "$baseUrl/Items/$Id/Images/$imageType?maxWidth=720&quality=90&tag=$tag&X-Emby-Token=$token"
    }

    private fun BaseItemDto.buildLogoImageUrl(token: String): String? {
        val tag = ImageTags?.Logo ?: return null
        return "$baseUrl/Items/$Id/Images/Logo?maxWidth=960&quality=90&tag=$tag&X-Emby-Token=$token"
    }

    private fun BaseItemDto.buildBackdropImageUrls(token: String): List<String> {
        if (BackdropImageTags.isEmpty()) return emptyList()
        return BackdropImageTags.mapIndexed { index, tag ->
            "$baseUrl/Items/$Id/Images/Backdrop/$index?maxWidth=1280&quality=80&tag=$tag&X-Emby-Token=$token"
        }
    }

    private fun BaseItemDto.parseDateCreated(): Instant? {
        val value = DateCreated?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun PlaybackMediaSourceDto.buildStreamOptions(
        itemId: String,
        baseUrl: String,
        token: String,
        playSessionId: String?,
    ): List<EmbyPlaybackStreamOption> {
        val requestHeaders = RequiredHttpHeaders.filterKeys { it.isNotBlank() }
        val directServerOption = resolveServerDirectUrl(baseUrl, token)?.let { directServerUrl ->
            EmbyPlaybackStreamOption(
                id = "server-direct",
                label = "服务器原链",
                description = "直接使用服务端返回的原始链路，适合可直解片源或已接管的 302 / STRM。",
                streamUrl = directServerUrl,
                requestHeaders = requestHeaders,
            )
        }
        val transcodingServerOption = resolveTranscodingUrl(baseUrl, token)?.let { transcodingUrl ->
            EmbyPlaybackStreamOption(
                id = "server-transcode",
                label = "兼容转码",
                description = "由 Emby 生成兼容链路，遇到 HEVC、10-bit 或特殊音轨时更稳。",
                streamUrl = transcodingUrl,
                requestHeaders = requestHeaders,
            )
        }
        val managedOptions = listOf(
            EmbyPlaybackStreamOption(
                id = "emby-direct",
                label = "直连源流",
                description = "直接请求 Emby 原始视频流，不额外限速。",
                streamUrl = buildManagedStreamUrl(
                    itemId = itemId,
                    baseUrl = baseUrl,
                    token = token,
                    playSessionId = playSessionId,
                    static = true,
                    bitrateLimit = null,
                ),
            ),
        )
        return (listOfNotNull(directServerOption) + managedOptions + listOfNotNull(transcodingServerOption))
            .distinctBy { option ->
            "${option.streamUrl}|${option.requestHeaders}"
        }
            .sortedBy { option -> option.streamPriority() }
    }

    private fun PlaybackMediaSourceDto.resolveServerDirectUrl(
        baseUrl: String,
        token: String,
    ): String? {
        val directUrl = DirectStreamUrl.toServerAbsoluteUrl(baseUrl)
            ?: Path.toServerAbsoluteUrl(baseUrl)
            ?: return null
        return if (AddApiKeyToDirectStreamUrl) {
            directUrl.appendApiKeyQueryIfMissing(token)
        } else {
            directUrl
        }
    }

    private fun PlaybackMediaSourceDto.resolveTranscodingUrl(
        baseUrl: String,
        token: String,
    ): String? {
        return TranscodingUrl
            .toServerAbsoluteUrl(baseUrl)
            ?.appendApiKeyQueryIfMissing(token)
    }

    private fun String?.toServerAbsoluteUrl(baseUrl: String): String? {
        val candidate = this?.trim().orEmpty()
        if (candidate.isBlank()) return null
        val absolute = when {
            candidate.startsWith("http://") || candidate.startsWith("https://") -> {
                candidate
            }
            candidate.startsWith("/videos/", ignoreCase = true) ||
                candidate.startsWith("/play/", ignoreCase = true) ||
                candidate.equals("/play", ignoreCase = true) -> {
                "${baseUrl.trimEnd('/')}$candidate"
            }
            else -> null
        }
        return absolute?.ensureEmbyProxyPlaySource()
    }

    private fun String.appendApiKeyQueryIfMissing(token: String): String {
        val parsed = runCatching { Uri.parse(this) }.getOrNull() ?: return this
        if (
            parsed.getQueryParameter("api_key") != null ||
            parsed.getQueryParameter("X-Emby-Token") != null
        ) {
            return this
        }
        return parsed.buildUpon()
            .appendQueryParameter("api_key", token)
            .build()
            .toString()
    }

    private fun String.ensureEmbyProxyPlaySource(): String {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return this
        val path = uri.path.orEmpty()
        if (!path.startsWith("/play/", ignoreCase = true) && !path.equals("/play", ignoreCase = true)) {
            return this
        }
        if (uri.getQueryParameter(PLAY_SOURCE_QUERY_PARAM) != null) {
            return this
        }
        return uri.buildUpon()
            .appendQueryParameter(PLAY_SOURCE_QUERY_PARAM, PLAY_SOURCE_EMBY_PROXY)
            .build()
            .toString()
    }

    private fun PlaybackMediaSourceDto.buildManagedStreamUrl(
        itemId: String,
        baseUrl: String,
        token: String,
        playSessionId: String?,
        static: Boolean,
        bitrateLimit: Int?,
    ): String {
        return buildString {
            append(baseUrl)
            append("/Videos/")
            append(itemId.urlEncode())
            append("/stream?")
            append("Static=")
            append(static.toString())
            append("&api_key=")
            append(token.urlEncode())
            append("&MediaSourceId=")
            append(Id.urlEncode())
            playSessionId
                ?.takeIf { it.isNotBlank() }
                ?.let { sessionId ->
                    append("&PlaySessionId=")
                    append(sessionId.urlEncode())
                }
            bitrateLimit?.let { bitrate ->
                append("&MaxStreamingBitrate=")
                append(bitrate)
            }
        }
    }

    private fun EmbyPlaybackStreamOption.streamPriority(): Int = when (id) {
        "server-direct" -> 0
        "emby-direct" -> 1
        "server-transcode" -> 2
        else -> 99
    }

    private fun PlaybackMediaSourceDto.isRemoteMediaSource(): Boolean {
        return IsRemote ||
            LocationType.equals("Remote", ignoreCase = true) ||
            Path.startsWith("http://", ignoreCase = true) ||
            Path.startsWith("https://", ignoreCase = true)
    }

    private fun PlaybackMediaSourceDto.shouldPreferCompatibilityFirst(): Boolean {
        val videoStream = MediaStreams.firstOrNull { it.Type.equals("Video", ignoreCase = true) } ?: return false
        val audioStreams = MediaStreams.filter { it.Type.equals("Audio", ignoreCase = true) }
        val codec = videoStream.Codec.orEmpty().lowercase(Locale.US)
        val profile = videoStream.Profile.orEmpty().lowercase(Locale.US)
        val dynamicRange = videoStream.VideoRange.orEmpty().lowercase(Locale.US)
        val extendedVideoType = videoStream.ExtendedVideoType.orEmpty().lowercase(Locale.US)
        val container = Container.orEmpty().lowercase(Locale.US)
        val path = Path.lowercase(Locale.US)

        val modernCodecRisk =
            codec in setOf("hevc", "h265", "av1", "vp9") ||
                (videoStream.BitDepth ?: 8) > 8 ||
                profile.contains("10") ||
                dynamicRange.contains("hdr") ||
                extendedVideoType.isNotBlank()

        if (modernCodecRisk) {
            return true
        }

        if (isRemoteMediaSource()) {
            return false
        }

        val legacyContainerRisk = container in setOf(
            "3gp",
            "avi",
            "flv",
            "mpeg",
            "mpg",
            "rm",
            "rmvb",
            "swf",
            "ts",
            "vob",
            "wmv",
        ) || path.endsWith(".264")
        val legacyVideoRisk = codec in setOf(
            "flv1",
            "gif",
            "mpeg1video",
            "rv30",
            "rv40",
            "vc1",
            "wmv1",
            "wmv2",
            "wmv3",
        )
        val legacyAudioRisk = audioStreams.any { stream ->
            stream.Codec.orEmpty().lowercase(Locale.US) in setOf(
                "amr_nb",
                "amr_wb",
                "cook",
                "mp2",
                "pcm_alaw",
                "pcm_mulaw",
                "wmav1",
                "wmav2",
            )
        }

        return legacyContainerRisk || legacyVideoRisk || legacyAudioRisk
    }

    private fun PlaybackMediaSourceDto.buildSubtitleTracks(
        itemId: String,
        baseUrl: String,
        token: String,
    ): List<EmbySubtitleTrack> {
        return MediaStreams.asSequence()
            .filter { it.Type == "Subtitle" && it.IsTextSubtitleStream && it.Index != null }
            .mapNotNull { stream ->
                val subtitleUrl = stream.resolveSubtitleUrl(
                    itemId = itemId,
                    mediaSourceId = Id,
                    baseUrl = baseUrl,
                    token = token,
                ) ?: return@mapNotNull null

                val mimeType = stream.subtitleMimeType() ?: return@mapNotNull null
                EmbySubtitleTrack(
                    index = stream.Index ?: return@mapNotNull null,
                    label = stream.DisplayTitle
                        ?: stream.DisplayLanguage
                        ?: stream.Language
                        ?: "字幕 ${stream.Index}",
                    language = stream.Language,
                    url = subtitleUrl,
                    mimeType = mimeType,
                    isDefault = stream.IsDefault || DefaultSubtitleStreamIndex == stream.Index,
                    isExternal = stream.IsExternal,
                )
            }
            .toList()
    }

    private fun PlaybackMediaStreamDto.resolveSubtitleUrl(
        itemId: String,
        mediaSourceId: String,
        baseUrl: String,
        token: String,
    ): String? {
        val directUrl = DeliveryUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    path
                } else {
                    "$baseUrl$path"
                }
            }
        if (directUrl != null) {
            return directUrl
        }

        val streamIndex = Index ?: return null
        val format = subtitleFileExtension() ?: return null
        return buildString {
            append(baseUrl)
            append("/Videos/")
            append(itemId.urlEncode())
            append("/")
            append(mediaSourceId.urlEncode())
            append("/Subtitles/")
            append(streamIndex)
            append("/Stream.")
            append(format)
            append("?api_key=")
            append(token.urlEncode())
        }
    }

    private fun PlaybackMediaSourceDto.buildInfoLine(
        subtitleCount: Int,
    ): String {
        val videoStream = MediaStreams.firstOrNull { it.Type.equals("Video", ignoreCase = true) }
        val audioStream = MediaStreams.firstOrNull { it.Type.equals("Audio", ignoreCase = true) }
        val videoCodec = videoStream?.Codec?.uppercase(Locale.US)
        val audioCodec = audioStream?.Codec?.uppercase(Locale.US)
        val resolution = videoStream?.resolutionLabel()

        return listOfNotNull(
            Container?.uppercase(Locale.US),
            resolution,
            videoCodec,
            audioCodec,
            subtitleCount.takeIf { it > 0 }?.let { "${it}条字幕" },
        ).joinToString(", ")
    }

    private fun PlaybackMediaSourceDto.buildInfoFields(
        subtitleTracks: List<EmbySubtitleTrack>,
    ): List<EmbyPlaybackInfoField> {
        val videoStream = MediaStreams.firstOrNull { it.Type.equals("Video", ignoreCase = true) }
        val audioStream = MediaStreams.firstOrNull { it.Type.equals("Audio", ignoreCase = true) }

        return buildList {
            Container
                ?.takeIf { it.isNotBlank() }
                ?.let { add(EmbyPlaybackInfoField("封装", it.uppercase(Locale.US))) }

            sourceTypeLabel()
                ?.let { add(EmbyPlaybackInfoField("来源", it)) }

            videoStream
                ?.resolutionLabel()
                ?.let { add(EmbyPlaybackInfoField("分辨率", it)) }

            videoStream
                ?.Codec
                ?.takeIf { it.isNotBlank() }
                ?.let { add(EmbyPlaybackInfoField("视频编码", it.uppercase(Locale.US))) }

            videoStream
                ?.frameRateLabel()
                ?.let { add(EmbyPlaybackInfoField("帧率", it)) }

            audioStream
                ?.Codec
                ?.takeIf { it.isNotBlank() }
                ?.let { add(EmbyPlaybackInfoField("音频编码", it.uppercase(Locale.US))) }

            audioStream
                ?.channelLayoutLabel()
                ?.let { add(EmbyPlaybackInfoField("声道", it)) }

            subtitleTracks
                .takeIf { it.isNotEmpty() }
                ?.let { tracks ->
                    add(EmbyPlaybackInfoField("字幕轨", "${tracks.size}条"))
                    tracks.firstOrNull { it.isDefault }?.label?.let { label ->
                        add(EmbyPlaybackInfoField("默认字幕", label))
                    }
                }
        }
    }

    private fun PlaybackMediaSourceDto.sourceTypeLabel(): String? = when {
        Path.startsWith("http://", ignoreCase = true) || Path.startsWith("https://", ignoreCase = true) ->
            "网络直链 / STRM"
        Path.isNotBlank() -> "Emby 媒体源"
        else -> null
    }

    private fun PlaybackMediaStreamDto.resolutionLabel(): String? {
        val width = Width
        val height = Height
        if (width == null || height == null || width <= 0 || height <= 0) return null
        return "${width}x${height}"
    }

    private fun PlaybackMediaStreamDto.frameRateLabel(): String? {
        val value = AverageFrameRate ?: return null
        if (value <= 0f) return null
        return String.format(Locale.US, "%.2f fps", value)
    }

    private fun PlaybackMediaStreamDto.channelLayoutLabel(): String? {
        val channels = Channels ?: return null
        if (channels <= 0) return null
        return when (channels) {
            1 -> "1.0"
            2 -> "2.0"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${channels}声道"
        }
    }

    private fun PlaybackMediaStreamDto.subtitleFileExtension(): String? = when (Codec?.lowercase(Locale.US)) {
        "srt", "subrip" -> "srt"
        "vtt", "webvtt" -> "vtt"
        "ass", "ssa" -> "ass"
        "ttml" -> "ttml"
        else -> "vtt"
    }

    private fun PlaybackMediaStreamDto.subtitleMimeType(): String? = when (subtitleFileExtension()) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass" -> "text/x-ssa"
        "ttml" -> "application/ttml+xml"
        else -> null
    }

    private fun buildSeasonEpisodeLabel(
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): String {
        val season = seasonNumber?.takeIf { it > 0 }
        val episode = episodeNumber?.takeIf { it > 0 }
        return when {
            season != null && episode != null -> "第${season}季 第${episode}集"
            episode != null -> "第${episode}集"
            else -> ""
        }
    }

    private fun formatRuntime(runTimeTicks: Long?): String {
        if (runTimeTicks == null || runTimeTicks <= 0L) return ""
        val minutes = runTimeTicks / 10_000_000L / 60L
        return when {
            minutes >= 60L -> String.format(Locale.US, "%d小时%02d分", minutes / 60L, minutes % 60L)
            minutes > 0L -> "${minutes}分钟"
            else -> ""
        }
    }

    private fun formatPlaybackPosition(playbackPositionTicks: Long): String {
        val totalSeconds = playbackPositionTicks / 10_000_000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun typeLabel(type: String?): String = when (type) {
        "Movie" -> "电影"
        "Series" -> "剧集"
        "Season" -> "分季"
        "Episode" -> "剧集"
        "CollectionFolder" -> "媒体库"
        else -> "视频"
    }

    private fun Long.msToTicks(): Long = this * 10_000L

    private fun currentDateTimeTicks(): Long {
        return System.currentTimeMillis() * 10_000L + 621355968000000000L
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
