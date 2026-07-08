package com.samfont.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.samfont.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    data class DownloadResult(
        val file: File,
        val uri: Uri
    )

    fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null
    ): DownloadResult {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) {
            updateDir.mkdirs()
        }

        val apkFile = File(updateDir, "SamFont-${info.versionName}.apk")
        val connection = openDownloadConnection(info.apkUrl)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0 }

        connection.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress?.invoke(totalRead, totalBytes)
                }
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return DownloadResult(file = apkFile, uri = uri)
    }

    private fun openDownloadConnection(url: String): HttpURLConnection {
        var currentUrl = url
        repeat(6) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SamFont/${BuildConfig.VERSION_NAME} Android")
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                setRequestProperty("Cache-Control", "no-cache")
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("下载更新失败：HTTP $code 缺少 Location")
                currentUrl = URL(URL(currentUrl), location).toString()
                connection.disconnect()
                return@repeat
            }

            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                throw IllegalStateException("下载更新失败：HTTP $code ${body.take(160)}")
            }

            return connection
        }

        throw IllegalStateException("下载更新失败：重定向次数过多")
    }

    fun buildInstallIntent(apkUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
