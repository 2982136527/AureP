package com.qiuhu.embyflow.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
    val id: String,
    val name: String = "",
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class ServerProfilesState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
) {
    val activeProfile: ServerProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
}

fun ServerProfile.displayName(): String {
    val trimmedName = name.trim()
    if (trimmedName.isNotBlank()) return trimmedName

    val normalizedUrl = serverUrl.trim().removeSuffix("/")
    if (normalizedUrl.isBlank()) return "未命名服务器"

    val withoutScheme = normalizedUrl
        .removePrefix("https://")
        .removePrefix("http://")
    return withoutScheme.substringBefore('/').ifBlank { "未命名服务器" }
}

fun ServerProfile.normalized(): ServerProfile = copy(
    name = name.trim(),
    serverUrl = serverUrl.trim().removeSuffix("/"),
    username = username.trim(),
    password = password.trim(),
)
