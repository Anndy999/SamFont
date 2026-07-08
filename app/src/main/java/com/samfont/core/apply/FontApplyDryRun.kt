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
        val shizuku = privilegeStatus.shizukuStatus
        val shizukuUsable = shizuku?.available == true &&
            shizuku.permissionGranted &&
            (shizuku.uid == 1000 || shizuku.uid == 2000)

        checks += "Shizuku usable: $shizukuUsable"
        checks += "Shizuku uid: ${shizuku?.uid ?: "unknown"}"
        checks += "Font file valid: $targetValid"

        return FontApplyDryRunReport(
            checks = checks,
            canProceedToApply = shizukuUsable && targetValid
        )
    }
}
