package com.samfont.core.apply

import android.content.Context
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.privilege.PrivilegeStatus
import com.samfont.core.samsung.SamsungFontApkGenerator
import com.samfont.core.samsung.SamsungFontVerifier
import com.samfont.core.shizuku.install.ShizukuPackageInstaller
import com.samfont.core.shizuku.install.ShizukuShellPackageInstaller
import java.io.File

class SamsungFontPackageBackend(
    private val context: Context,
    private val privilegeStatus: PrivilegeStatus,
    private val generator: SamsungFontApkGenerator = SamsungFontApkGenerator(context),
    private val installer: ShizukuPackageInstaller = ShizukuShellPackageInstaller(),
    private val verifier: SamsungFontVerifier = SamsungFontVerifier()
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
        if (shizuku?.available != true) {
            return FontApplyResult(false, "Shizuku 未运行，请先启动 Shizuku。", "status=$shizuku")
        }
        if (!shizuku.permissionGranted) {
            return FontApplyResult(false, "Shizuku 未授权，无法安装字体包。", "status=$shizuku")
        }
        if (shizuku.uid != 1000 && shizuku.uid != 2000) {
            return FontApplyResult(false, "Shizuku UID=${shizuku.uid}，不允许安装字体包。", "status=$shizuku")
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

        val shellCheck = com.samfont.core.shizuku.ShizukuBridge.runShell("pm path android >/dev/null && pm list packages android")
        if (!shellCheck.success) {
            return FontApplyResult(
                success = false,
                message = "Shizuku shell 无法执行 pm 命令。",
                backendLog = formatShellCheck(shellCheck.command, shellCheck.exitCode, shellCheck.stdout, shellCheck.stderr)
            )
        }

        return runCatching {
            val generated = generator.generate(fontFamily)
            val install = installer.installApk(generated.apk, generated.spec.packageName)
            if (!install.success) {
                return FontApplyResult(
                    success = false,
                    message = install.message,
                    backendLog = generated.log + "\n" + install.log
                )
            }

            val verification = verifier.verifyPackageInstalled(generated.spec.packageName)
            if (!verification.installed) {
                return FontApplyResult(
                    success = false,
                    message = verification.message,
                    backendLog = generated.log + "\n" + install.log + "\n" + verification.log
                )
            }

            FontApplyResult(
                success = true,
                message = "字体包已安装，请在 Samsung 系统字体设置中选择该字体。",
                backendLog = buildString {
                    appendLine(generated.log)
                    appendLine(install.log)
                    appendLine(verification.log)
                    appendLine("visibleToSamsung=${verification.visibleToSamsung}")
                    appendLine("currentlyApplied=${verification.currentlyApplied}")
                }
            )
        }.getOrElse { throwable ->
            FontApplyResult(
                success = false,
                message = "字体包生成失败，请查看诊断日志。",
                backendLog = throwable.stackTraceToString()
            )
        }
    }

    override fun rollback(): FontApplyResult {
        return FontApplyResult(false, "Samsung 字体回滚后端尚未实现。", "Rollback refused.")
    }

    private fun formatShellCheck(command: String, exitCode: Int, stdout: String, stderr: String): String {
        return buildString {
            appendLine("$ $command")
            appendLine("exitCode=$exitCode")
            if (stdout.isNotBlank()) appendLine("stdout:\n$stdout")
            if (stderr.isNotBlank()) appendLine("stderr:\n$stderr")
        }
    }
}
