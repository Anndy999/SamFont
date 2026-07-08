package com.samfont.core.apply

data class FontApplyPlan(
    val fontId: String,
    val targetHash: String,
    val alreadyApplied: Boolean,
    val needsCopy: Boolean,
    val needsPermissionFix: Boolean,
    val needsConfigWrite: Boolean,
    val needsRefresh: Boolean
) {
    val noOp: Boolean
        get() = alreadyApplied || (!needsCopy && !needsPermissionFix && !needsConfigWrite && !needsRefresh)
}

data class FontApplyResult(
    val success: Boolean,
    val message: String,
    val backendLog: String? = null
)
