package com.samfont.core.privilege

data class PrivilegeStatus(
    val uid: Int,
    val appId: Int,
    val isUid1000: Boolean,
    val canApplySystemFont: Boolean,
    val title: String,
    val message: String,
    val installMode: String = "unknown",
    val processUid: Int = uid,
    val osUid: Int = uid,
    val effectiveUid: Int = uid,
    val packageUid: Int? = null,
    val realUid: Int? = null,
    val savedSetUid: Int? = null,
    val fileSystemUid: Int? = null,
    val selinuxContext: String? = null,
    val diagnostics: List<String> = emptyList(),
    val detectionSource: String = "Process.myUid()"
)
