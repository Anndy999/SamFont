package com.samfont.core.update

import com.samfont.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object GithubReleaseUpdateBackend {
    fun fetchLatest(): UpdateInfo {
        val url = URL(UpdateConfig.RELEASES_API_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SamFont")
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("GitHub 更新检查失败：HTTP ${connection.responseCode}")
        }

        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(payload)
        val tagName = json.optString("tag_name").takeIf { it.isNotBlank() }
            ?: json.optString("name")
        val title = json.optString("name").ifBlank { tagName }
        val body = json.optString("body")
        val publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
        val apkUrl = extractApkUrl(json.optJSONArray("assets"))
            ?: throw IllegalStateException("Release 中没有找到 APK 资产")

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

    fun isConfigured(): Boolean {
        return !UpdateConfig.RELEASES_API_URL.contains("OWNER", ignoreCase = true)
    }

    private fun extractApkUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return url
            }
        }
        return null
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

        val computed = major * 10000 + minor * 100 + patch
        return computed
    }
}
