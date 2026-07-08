package com.samfont.core.update

import com.samfont.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object GithubReleaseUpdateBackend {
    fun fetchLatest(): UpdateInfo {
        return runCatching {
            fetchLatestFromApi()
        }.getOrElse {
            fetchLatestFromRedirect()
        }
    }

    fun isConfigured(): Boolean {
        return UpdateConfig.OWNER.isNotBlank() && UpdateConfig.REPO.isNotBlank()
    }

    private fun fetchLatestFromApi(): UpdateInfo {
        val payload = httpGetText(UpdateConfig.RELEASES_API_URL)
        val json = JSONObject(payload)
        val tagName = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: json.optString("name")
        val title = json.optString("name").ifBlank { tagName }
        val body = json.optString("body")
        val publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
        val apkUrl = extractApkUrl(json.optJSONArray("assets"))
            ?: buildNormalApkUrl(tagName)

        return UpdateInfo(
            tagName = tagName,
            versionCode = parseVersionCode(tagName),
            versionName = normalizeVersionName(tagName),
            title = title,
            body = body,
            apkUrl = apkUrl,
            publishedAt = publishedAt
        )
    }

    private fun fetchLatestFromRedirect(): UpdateInfo {
        val tagName = resolveLatestTagFromRedirect()
        return UpdateInfo(
            tagName = tagName,
            versionCode = parseVersionCode(tagName),
            versionName = normalizeVersionName(tagName),
            title = "SamFont $tagName",
            body = "GitHub API 不可用时使用 releases/latest 兜底检查。",
            apkUrl = buildNormalApkUrl(tagName),
            publishedAt = null
        )
    }

    private fun httpGetText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setCommonHeaders()
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("GitHub 更新检查失败：HTTP $code ${payload.take(160)}")
        }
        return payload
    }

    private fun resolveLatestTagFromRedirect(): String {
        val connection = (URL(UpdateConfig.LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setCommonHeaders()
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        val location = connection.getHeaderField("Location")
        val candidate = location
            ?.substringAfterLast("/tag/", missingDelimiterValue = "")
            ?.substringBefore("?")
            ?.takeIf { it.isNotBlank() }

        if (candidate != null) return candidate

        val finalUrl = connection.url.toString()
        val finalCandidate = finalUrl
            .substringAfterLast("/tag/", missingDelimiterValue = "")
            .substringBefore("?")
            .takeIf { it.isNotBlank() }

        return finalCandidate
            ?: throw IllegalStateException("无法从 GitHub releases/latest 解析最新版本")
    }

    private fun extractApkUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        var fallbackUrl: String? = null

        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                if (name.contains("normal", ignoreCase = true)) {
                    return url
                }
                if (!name.contains("system", ignoreCase = true) && fallbackUrl == null) {
                    fallbackUrl = url
                } else if (fallbackUrl == null) {
                    fallbackUrl = url
                }
            }
        }
        return fallbackUrl
    }

    private fun buildNormalApkUrl(tagName: String): String {
        return "${UpdateConfig.RELEASE_DOWNLOAD_BASE_URL}/$tagName/SamFont-normal-debug-$tagName.apk"
    }

    private fun normalizeVersionName(tagName: String): String {
        val cleaned = tagName.trim().removePrefix("v").removePrefix("V")
        return if (cleaned.isBlank()) BuildConfig.VERSION_NAME else cleaned
    }

    private fun parseVersionCode(tagName: String): Int {
        val cleaned = normalizeVersionName(tagName)
        val parts = cleaned.split('.', '-', '_').filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0

        var major = 0
        var minor = 0
        var patch = 0
        parts.take(3).forEachIndexed { index, part ->
            val value = part.filter { it.isDigit() }.ifBlank { "0" }.toIntOrNull() ?: 0
            when (index) {
                0 -> major = value
                1 -> minor = value
                2 -> patch = value
            }
        }

        return major * 10000 + minor * 100 + patch
    }

    private fun HttpURLConnection.setCommonHeaders() {
        setRequestProperty("User-Agent", "SamFont/${BuildConfig.VERSION_NAME} Android")
        setRequestProperty("Cache-Control", "no-cache")
    }
}
