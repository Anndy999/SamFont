package com.samfont.core.apply

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.privilege.PrivilegeStatus
import com.samfont.core.samsung.SamsungFontApkGenerator
import com.samfont.core.samsung.SamsungFontApplyMode
import com.samfont.core.samsung.SamsungMiscPolicyFontApplier
import com.samfont.core.samsung.SamsungFontVerifier
import com.samfont.core.shizuku.install.ShizukuPackageInstaller
import com.samfont.core.shizuku.install.ShizukuShellPackageInstaller
import java.io.File

class SamsungFontPackageBackend(
    private val context: Context,
    private val privilegeStatus: PrivilegeStatus,
    private val generator: SamsungFontApkGenerator = SamsungFontApkGenerator(context),
    private val installer: ShizukuPackageInstaller = ShizukuShellPackageInstaller(),
    private val verifier: SamsungFontVerifier = SamsungFontVerifier(),
    private val fontApplier: SamsungMiscPolicyFontApplier = SamsungMiscPolicyFontApplier(),
    private val applyMode: SamsungFontApplyMode = SamsungFontApplyMode.Auto
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
            needsConfigWrite = false,
            needsRefresh = !alreadyApplied
        )
    }

    override suspend fun apply(plan: FontApplyPlan, fontFamily: FontFamilyModel): FontApplyResult {
        val shizuku = privilegeStatus.shizukuStatus
        val shizukuLog = buildShizukuLog()
        if (shizuku?.available != true) {
            return FontApplyResult(false, "Shizuku 未运行，请先启动 Shizuku。", shizukuLog)
        }
        if (!shizuku.permissionGranted) {
            return FontApplyResult(false, "Shizuku 未授权，无法安装字体包。", shizukuLog)
        }
        if (shizuku.uid != 1000) {
            return FontApplyResult(false, "需要 Shizuku UID1000 权限才能安装 Samsung 字体包。", shizukuLog)
        }

        val fontFileModel = fontFamily.files.firstOrNull()
            ?: return FontApplyResult(false, "字体文件不存在。", "font.files is empty")
        val fontFile = File(fontFileModel.path)
        if (!fontFile.exists()) {
            return FontApplyResult(false, "字体文件不存在。", fontFile.absolutePath)
        }
        if (!FontRepository.isValidFontFile(fontFile)) {
            return FontApplyResult(false, "字体文件无效。", fontFile.absolutePath)
        }

        return runCatching {
            val generated = generator.generate(fontFamily)
            val structure = com.samfont.core.samsung.ApkStructureVerifier.requireValidResourcesArsc(generated.apk)
            val install = installer.installApk(generated.apk, generated.packageName)
            if (!install.success) {
                return FontApplyResult(
                    success = false,
                    message = install.message,
                    backendLog = shizukuLog + "\n" + generated.log + "\n" + install.log
                )
            }

            val verification = verifier.verifyPackageInstalled(generated.packageName)
            if (!verification.installed) {
                return FontApplyResult(
                    success = false,
                    message = verification.message,
                    backendLog = shizukuLog + "\n" + generated.log + "\n" + install.log + "\n" + verification.log
                )
            }
            val applyResult = fontApplier.applyAndVerify(generated, applyMode)

            FontApplyResult(
                success = true,
                message = applyResult.message,
                backendLog = buildString {
                    appendLine(shizukuLog)
                    appendLine(generated.log)
                    append(structure.toLog("resources.arsc install precheck:"))
                    appendLine(install.log)
                    appendLine(verification.log)
                    appendLine(applyResult.log)
                    appendLine("visibleToSamsung=${verification.visibleToSamsung}")
                    appendLine("currentlyApplied=${verification.currentlyApplied}")
                    appendLine("autoApplied=${applyResult.applied}")
                },
                applied = applyResult.applied
            )
        }.getOrElse { throwable ->
            FontApplyResult(
                success = false,
                message = "字体包生成失败，请查看诊断日志。",
                backendLog = shizukuLog + "\n" + throwable.stackTraceToString()
            )
        }
    }

    override fun rollback(): FontApplyResult {
        return FontApplyResult(false, "Samsung 字体回滚后端尚未实现。", "Rollback refused.")
    }

    private fun buildShizukuLog(): String {
        val shizuku = privilegeStatus.shizukuStatus
        return buildString {
            appendLine("Shizuku available=${shizuku?.available ?: false}")
            appendLine("Shizuku permissionGranted=${shizuku?.permissionGranted ?: false}")
            appendLine("Shizuku uid=${shizuku?.uid ?: "unknown"}")
            appendLine("Shizuku source=${shizuku?.source ?: "unknown"}")
        }
    }
}
