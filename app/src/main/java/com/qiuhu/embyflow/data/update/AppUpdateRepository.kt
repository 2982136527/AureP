package com.qiuhu.embyflow.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdateState(
    val currentVersion: String,
    val latestVersion: String? = null,
    val hasUpdate: Boolean = false,
    val updatePageUrl: String = GitHubReleasePageUrl,
    val downloadUrl: String = "",
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
)

class AppUpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun checkForUpdate(currentVersion: String): AppUpdateState = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url(GitHubOutputMetadataUrl)
                .get()
                .build(),
        ).execute()

        if (!response.isSuccessful) {
            throw IOException("更新检查失败：${response.code}")
        }

        val body = response.body?.string().orEmpty()
        if (body.isBlank()) {
            throw IOException("更新检查失败：返回内容为空")
        }

        val metadata = runCatching {
            json.decodeFromString<ReleaseMetadataDto>(body)
        }.getOrElse { throwable ->
            throw IOException("更新检查失败：${throwable.message ?: "无法解析版本信息"}", throwable)
        }

        val latestVersion = metadata.versionName()
            ?: throw IOException("更新检查失败：未找到版本号")
        val hasUpdate = compareVersions(latestVersion, currentVersion) > 0
        val releasePageUrl = buildReleasePageUrl(latestVersion)
        AppUpdateState(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            hasUpdate = hasUpdate,
            updatePageUrl = releasePageUrl,
            downloadUrl = buildReleaseDownloadUrl(latestVersion),
            isChecking = false,
        )
    }

    private fun ReleaseMetadataDto.versionName(): String? {
        return elements.firstNotNullOfOrNull { element ->
            element.versionName?.trim()?.takeIf(String::isNotBlank)
        }
    }
}

@Serializable
private data class ReleaseMetadataDto(
    val elements: List<ReleaseMetadataElementDto> = emptyList(),
)

@Serializable
private data class ReleaseMetadataElementDto(
    val versionName: String? = null,
)

private const val GitHubOwner = "2982136527"
private const val GitHubRepo = "AureP-app"
private const val GitHubReleasePageUrl = "https://github.com/$GitHubOwner/$GitHubRepo/tree/main/releases"
private const val GitHubOutputMetadataUrl =
    "https://raw.githubusercontent.com/$GitHubOwner/$GitHubRepo/main/releases/output-metadata.json"

private fun buildReleasePageUrl(versionName: String): String {
    val normalized = versionName.trim().removePrefix("v")
    return "https://github.com/$GitHubOwner/$GitHubRepo/blob/main/releases/AureP-v$normalized.apk"
}

private fun buildReleaseDownloadUrl(versionName: String): String {
    val normalized = versionName.trim().removePrefix("v")
    return "https://github.com/$GitHubOwner/$GitHubRepo/raw/main/releases/AureP-v$normalized.apk"
}

private fun compareVersions(left: String, right: String): Int {
    val leftParts = left.normalizedVersionParts()
    val rightParts = right.normalizedVersionParts()
    val size = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until size) {
        val leftValue = leftParts.getOrElse(index) { 0 }
        val rightValue = rightParts.getOrElse(index) { 0 }
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

private fun String.normalizedVersionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { part -> part.toIntOrNull() }
}
