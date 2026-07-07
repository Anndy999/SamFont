package com.samfont.core.apply

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.preview.FontPreviewEngine
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
        val systemFontDirs = listOf("/system/fonts", "/product/fonts", "/system/etc", "/product/etc")
        val existingSystemDirs = systemFontDirs.filter { File(it).exists() }
        val backupDir = File(context.filesDir, "font-backups")
        val targetLoadable = previewFile?.let { FontPreviewEngine.loadTypeface(it, it.weight) != null } == true
        val shizuku = privilegeStatus.shizukuStatus

        checks += "App UID1000: ${privilegeStatus.isUid1000}"
        checks += "Shizuku ROOT: ${shizuku?.canDryRunAsRoot == true}"
        checks += "Shizuku shell diagnostics: ${shizuku?.canDiagnose == true && shizuku.canDryRunAsRoot.not()}"
        checks += "系统字体路径存在: ${existingSystemDirs.joinToString().ifBlank { "无" }}"
        checks += "目标字体可加载: $targetLoadable"
        checks += "备份目录可用: ${backupDir.exists() || backupDir.mkdirs()}"

        val canProceed = targetLoadable &&
            (privilegeStatus.isUid1000 || shizuku?.canDryRunAsRoot == true) &&
            existingSystemDirs.isNotEmpty()

        return FontApplyDryRunReport(
            checks = checks,
            canProceedToRootDryRun = canProceed
        )
    }
}
