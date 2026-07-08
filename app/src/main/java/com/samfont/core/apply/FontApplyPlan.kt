package com.samfont.core.apply

import java.io.File

data class FontApplyPlan(
    val fontId: String,
    val sourceFiles: List<File>,
    val installedFiles: List<File>,
    val currentHash: String?,
    val targetHash: String,
    val needsCopy: Boolean,
    val needsPermissionFix: Boolean,
    val needsConfigWrite: Boolean,
    val needsCacheRefresh: Boolean
) {
    val noOp: Boolean
        get() = !needsCopy && !needsPermissionFix && !needsConfigWrite && !needsCacheRefresh
}
