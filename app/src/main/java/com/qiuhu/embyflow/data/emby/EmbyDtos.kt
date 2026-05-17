package com.qiuhu.embyflow.data.emby

import kotlinx.serialization.Serializable

@Serializable
data class PublicSystemInfoDto(
    val ServerName: String = "",
    val Version: String = "",
    val Id: String = "",
)

@Serializable
data class AuthResponseDto(
    val User: UserDto,
    val AccessToken: String,
    val ServerId: String = "",
)

@Serializable
data class UserDto(
    val Id: String,
    val Name: String,
)

@Serializable
data class QueryResultDto(
    val Items: List<BaseItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)

@Serializable
data class BaseItemDto(
    val Id: String,
    val Name: String,
    val Type: String? = null,
    val DateCreated: String? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val SeasonId: String? = null,
    val ParentId: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val Overview: String? = null,
    val PremiereDate: String? = null,
    val ProductionYear: Int? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Double? = null,
    val RunTimeTicks: Long? = null,
    val PrimaryImageAspectRatio: Double? = null,
    val Genres: List<String> = emptyList(),
    val People: List<PersonDto> = emptyList(),
    val ImageTags: ImageTagsDto? = null,
    val BackdropImageTags: List<String> = emptyList(),
    val UserData: UserDataDto? = null,
    val CollectionType: String? = null,
    val ChildCount: Int? = null,
    val RecursiveItemCount: Int? = null,
    val IsFolder: Boolean = false,
)

@Serializable
data class PersonDto(
    val Name: String = "",
    val Id: String = "",
    val Type: String? = null,
    val Role: String? = null,
    val PrimaryImageTag: String? = null,
)

@Serializable
data class ImageTagsDto(
    val Primary: String? = null,
    val Thumb: String? = null,
    val Logo: String? = null,
)

@Serializable
data class UserDataDto(
    val UnplayedItemCount: Int? = null,
    val PlaybackPositionTicks: Long = 0,
    val PlayCount: Int = 0,
    val IsFavorite: Boolean = false,
    val Played: Boolean = false,
)

@Serializable
data class PlaybackInfoDto(
    val MediaSources: List<PlaybackMediaSourceDto> = emptyList(),
    val PlaySessionId: String? = null,
)

@Serializable
data class PlaybackMediaSourceDto(
    val Id: String,
    val Path: String,
    val DirectStreamUrl: String? = null,
    val TranscodingUrl: String? = null,
    val Name: String? = null,
    val Container: String? = null,
    val LocationType: String? = null,
    val IsRemote: Boolean = false,
    val SupportsDirectPlay: Boolean = false,
    val SupportsDirectStream: Boolean = false,
    val SupportsTranscoding: Boolean = false,
    val TranscodingContainer: String? = null,
    val TranscodingSubProtocol: String? = null,
    val AddApiKeyToDirectStreamUrl: Boolean = false,
    val RequiredHttpHeaders: Map<String, String> = emptyMap(),
    val MediaStreams: List<PlaybackMediaStreamDto> = emptyList(),
    val DefaultAudioStreamIndex: Int? = null,
    val DefaultSubtitleStreamIndex: Int? = null,
)

@Serializable
data class PlaybackMediaStreamDto(
    val Type: String? = null,
    val Codec: String? = null,
    val Language: String? = null,
    val DisplayLanguage: String? = null,
    val DisplayTitle: String? = null,
    val Index: Int? = null,
    val Width: Int? = null,
    val Height: Int? = null,
    val Channels: Int? = null,
    val AverageFrameRate: Float? = null,
    val RealFrameRate: Float? = null,
    val BitDepth: Int? = null,
    val Profile: String? = null,
    val Level: Double? = null,
    val VideoRange: String? = null,
    val IsInterlaced: Boolean? = null,
    val ExtendedVideoType: String? = null,
    val ExtendedVideoSubType: String? = null,
    val IsDefault: Boolean = false,
    val DeliveryUrl: String? = null,
    val IsExternal: Boolean = false,
    val IsTextSubtitleStream: Boolean = false,
)
