package com.samfont.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuBridge {
    const val REQUEST_CODE = 6201

    fun check(): ShizukuStatus {
        return runCatching {
            if (!Shizuku.pingBinder()) {
                return ShizukuStatus(
                    available = false,
                    permissionGranted = false,
                    uid = null,
                    source = ShizukuSource.NOT_RUNNING,
                    message = "Shizuku 未运行"
                )
            }

            val permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val uid = runCatching { Shizuku.getUid() }.getOrNull()
            val source = when (uid) {
                1000 -> ShizukuSource.SYSTEM_UID
                0 -> ShizukuSource.ROOT
                2000 -> ShizukuSource.ADB_SHELL
                null -> ShizukuSource.UNKNOWN
                else -> ShizukuSource.UNKNOWN
            }
            val message = when {
                !permissionGranted -> "Shizuku 可用，但未授权"
                source == ShizukuSource.SYSTEM_UID -> "Shizuku UID1000 模式"
                source == ShizukuSource.ROOT -> "Shizuku ROOT 模式"
                source == ShizukuSource.ADB_SHELL -> "Shizuku ADB shell 模式，仅允许诊断"
                else -> "Shizuku UID: ${uid ?: "未知"}"
            }

            ShizukuStatus(
                available = true,
                permissionGranted = permissionGranted,
                uid = uid,
                source = if (permissionGranted) source else ShizukuSource.DENIED,
                message = message
            )
        }.getOrElse { throwable ->
            ShizukuStatus(
                available = false,
                permissionGranted = false,
                uid = null,
                source = ShizukuSource.UNAVAILABLE,
                message = throwable.message ?: "Shizuku API 不可用"
            )
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
