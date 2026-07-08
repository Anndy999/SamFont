package com.samfont.core.shizuku.install

import java.io.File

interface ShizukuPackageInstaller {
    suspend fun installApk(apk: File, packageName: String): ShizukuInstallResult
    suspend fun isPackageInstalled(packageName: String): Boolean
    suspend fun uninstallPackage(packageName: String): ShizukuInstallResult
}

data class ShizukuInstallResult(
    val success: Boolean,
    val packageName: String,
    val message: String,
    val log: String
)
