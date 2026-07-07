package com.samfont.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    fun downloadApk(context: Context, info: UpdateInfo): Uri {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) {
            updateDir.mkdirs()
        }

        val apkFile = File(updateDir, "SamFont-${info.versionName}.apk")
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "SamFont")
        }

        connection.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
    }

    fun buildInstallIntent(context: Context, apkUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
