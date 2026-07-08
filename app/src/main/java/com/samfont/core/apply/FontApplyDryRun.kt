package com.samfont.core.apply

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.privilege.PrivilegeStatus
import java.io.File

data class FontApplyDryRunReport(
    val checks: List<String>,
    val canProceedToApply: Boolean
)

object FontApplyDryRun {
    fun run(
        context: Context,
        fontFamily: FontFamilyModel,
        privilegeStatus: PrivilegeStatus
    ): FontApplyDryRunReport {
        val checks = mutableListOf<String>()
        val file = fontFamily.files.firstOrNull()?.let { File(it.path) }
        val targetValid = file?.let { it.exists() && FontRepository.isValidFontFile(it) } == true
        val installed = fontFamily.files.firstOrNull()?.sha256?.let {
            FontRepository.findInstalledFile(context, it) != null
        } == true
        val shizuku = privilegeStatus.shizukuStatus

        checks += "Shizuku UID1000: ${shizuku?.canOperateSystemFonts == true}"
        checks += "目标字体已安装: $installed"
        checks += "目标字体文件有效: $targetValid"

        return FontApplyDryRunReport(
            checks = checks,
            canProceedToApply = privilegeStatus.canApplySystemFont && installed && targetValid
        )
    }
}
