package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel
import java.io.File

class StubFontApplyBackend : FontApplyBackend {
    override fun getCurrentFont(): String = "Samsung Default"

    override fun dryRun(fontFamily: FontFamilyModel): Boolean = true

    override fun createPlan(
        fontFamily: FontFamilyModel,
        currentHash: String?,
        installedExists: Boolean
    ): FontApplyPlan {
        val sourceFiles = fontFamily.files.map { File(it.path) }
        val targetHash = fontFamily.files.firstOrNull()?.sha256.orEmpty()

        return FontApplyPlan(
            fontId = fontFamily.id,
            sourceFiles = sourceFiles,
            installedFiles = sourceFiles,
            currentHash = currentHash,
            targetHash = targetHash,
            needsCopy = !installedExists,
            needsPermissionFix = !installedExists,
            needsConfigWrite = currentHash != targetHash,
            needsCacheRefresh = currentHash != targetHash
        )
    }

    override fun applyPlan(plan: FontApplyPlan): Boolean {
        // Stub 不执行真实系统操作；上层只根据 plan 判断是否需要继续。
        return plan.noOp
    }

    override fun apply(fontFamily: FontFamilyModel): Boolean {
        // 当前阶段只保留接口，不修改系统字体文件。
        return false
    }

    override fun rollback(): Boolean = true
}
