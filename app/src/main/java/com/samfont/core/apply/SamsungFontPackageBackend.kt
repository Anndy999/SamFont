package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel
import com.samfont.core.privilege.PrivilegeStatus

class SamsungFontPackageBackend(
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

        val packageName = "com.monotype.android.font.samfont.${plan.fontId.take(12)}"
        return FontApplyResult(
            success = false,
            message = "Samsung 字体 APK 生成/安装后端尚未完成，未更改系统字体。",
            backendLog = buildString {
                appendLine("Required backend pipeline:")
                appendLine("1. Generate Samsung font APK for package=$packageName")
                appendLine("2. Sign APK")
                appendLine("3. Install through Shizuku PackageInstaller session")
                appendLine("4. Verify package and Samsung font list")
                appendLine("5. Apply only after verification")
                appendLine("Current Shizuku UID=${shizuku.uid}, source=${shizuku.source}")
                appendLine("fontId=${plan.fontId}")
                appendLine("targetHash=${plan.targetHash}")
                append("installedPath=${fontFamily.files.firstOrNull()?.path.orEmpty()}")
            }
        )
    }

    override fun rollback(): FontApplyResult {
        return FontApplyResult(
            success = false,
            message = "Samsung 字体回滚后端尚未实现。",
            backendLog = "Rollback refused."
        )
    }
}
