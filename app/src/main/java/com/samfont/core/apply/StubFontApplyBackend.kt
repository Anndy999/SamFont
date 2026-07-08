package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel

class StubFontApplyBackend : FontApplyBackend {
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
        return FontApplyResult(
            success = false,
            message = "系统字体应用后端尚未接入，未执行系统修改。",
            backendLog = "Stub backend refused apply. fontId=${plan.fontId}, hash=${plan.targetHash}"
        )
    }

    override fun rollback(): FontApplyResult {
        return FontApplyResult(
            success = false,
            message = "回滚后端尚未接入。",
            backendLog = "Stub backend refused rollback."
        )
    }
}
