package jp.swapcalendar.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UpdateInfo(val version: String, val releaseUrl: String)

class ReleaseUpdateChecker(
    private val client: HttpClient,
    private val currentVersion: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findUpdate(): UpdateInfo? {
        val response = client.get(LATEST_RELEASE_API) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "SwapCalendar-Android/$currentVersion")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) return null
        val release = json.decodeFromString<GitHubRelease>(response.bodyAsText())
        val latest = release.tagName.removePrefix("v")
        return if (isNewerVersion(latest, currentVersion)) {
            UpdateInfo(latest, release.htmlUrl)
        } else {
            null
        }
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/ASDFFRGH/swap_calender/releases/latest"

        internal fun isNewerVersion(candidate: String, current: String): Boolean {
            val candidateParts = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val size = maxOf(candidateParts.size, currentParts.size)
            return (0 until size).firstNotNullOfOrNull { index ->
                val left = candidateParts.getOrElse(index) { 0 }
                val right = currentParts.getOrElse(index) { 0 }
                when {
                    left > right -> true
                    left < right -> false
                    else -> null
                }
            } ?: false
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)
