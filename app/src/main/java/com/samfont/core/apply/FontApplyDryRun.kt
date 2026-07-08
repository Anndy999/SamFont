package com.samfont.core.apply

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.privilege.PrivilegeStatus
import java.io.File

data class FontApplyDryRunReport(
    val checks: List<String>,
    val canProceedToRootDryRun: Boolean
)

object FontApplyDryRun {
    fun run(
        context: Context,
        fontFamily: FontFamilyModel,
        privilegeStatus: PrivilegeStatus
    ): FontApplyDryRunReport {
        val checks = mutableListOf<String>()
        val previewFile = fontFamily.files.firstOrNull()
        val sourceFile = previewFile?.let { File(it.path) }
        val systemFontDirs = listOf("/system/fonts", "/product/fonts", "/system/etc", "/product/etc")
        val existingSystemDirs = systemFontDirs.filter { File(it).exists() }
        val backupDir = File(context.filesDir, "font-backups")
        val targetValid = sourceFile?.let { it.exists() && FontRepository.isValidFontFile(it) } == true
        val shizuku = privilegeStatus.shizukuStatus

        checks += "App UID1000: ${privilegeStatus.isUid1000}"
        checks += "Shizuku UID1000: ${shizuku?.canOperateSystemFonts == true}"
        checks += "Shizuku diagnostics: ${shizuku?.canDiagnose == true}"
        checks += "系统字体路径存在: ${existingSystemDirs.joinToString().ifBlank { "无" }}"
        checks += "目标字体文件有效: $targetValid"
        checks += "备份目录可用: ${backupDir.exists() || backupDir.mkdirs()}"

        val canProceed = targetValid &&
            privilegeStatus.canApplySystemFont &&
            existingSystemDirs.isNotEmpty()

        return FontApplyDryRunReport(
            checks = checks,
            canProceedToRootDryRun = canProceed
        )
    }
}
