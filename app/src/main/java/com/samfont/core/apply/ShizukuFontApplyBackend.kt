package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel
import com.samfont.core.privilege.PrivilegeStatus

class ShizukuFontApplyBackend(
    private val privilegeStatus: PrivilegeStatus
) : FontApplyBackend {
    override fun getCurrentFont(): String = "Samsung Default"

    override fun createPlan(
        fontFamily: FontFamilyModel,
        currentHash: String?,
        installedExists: Boolean
    ): FontApplyPlan {
        val targetHash = fontFamily.files.firstOrNull()?.sha256.orEmpty()
        val alreadyApplied = currentHash == targetHash && targetHash.isNotBlank()
        return FontApplyPlan(
            fontId = fontFamily.id,
            targetHash = targetHash,
            alreadyApplied = alreadyApplied,
            needsCopy = !installedExists,
            needsPermissionFix = !installedExists,
            needsConfigWrite = !alreadyApplied,
            needsRefresh = !alreadyApplied
        )
    }

    override fun apply(plan: FontApplyPlan, fontFamily: FontFamilyModel): FontApplyResult {
        if (plan.alreadyApplied) {
            return FontApplyResult(
                success = true,
                message = "当前已应用",
                backendLog = "Skip apply: target hash already active."
            )
        }

        val shizuku = privilegeStatus.shizukuStatus
        if (shizuku?.canOperateSystemFonts != true) {
            return FontApplyResult(
                success = false,
                message = "Shizuku 未授权或 server UID 不是 1000，不能应用系统字体。",
                backendLog = "available=${shizuku?.available}, granted=${shizuku?.permissionGranted}, uid=${shizuku?.uid}, source=${shizuku?.source}"
            )
        }

        return FontApplyResult(
            success = false,
            message = "已调用 Shizuku 字体后端，但真实系统字体写入尚未实现，未更改系统字体。",
            backendLog = buildString {
                appendLine("Shizuku UID=${shizuku.uid}, source=${shizuku.source}")
                appendLine("fontId=${plan.fontId}")
                appendLine("targetHash=${plan.targetHash}")
                appendLine("installedPath=${fontFamily.files.firstOrNull()?.path.orEmpty()}")
                append("Refused to fake Applied state.")
            }
        )
    }

    override fun rollback(): FontApplyResult {
        return FontApplyResult(
            success = false,
            message = "Shizuku 回滚后端尚未实现。",
            backendLog = "Rollback refused."
        )
    }
}
