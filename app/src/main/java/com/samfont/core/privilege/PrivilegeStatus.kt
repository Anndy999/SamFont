package com.samfont.core.privilege

data class PrivilegeStatus(
    val uid: Int,
    val appId: Int,
    val isUid1000: Boolean,
    val canApplySystemFont: Boolean,
    val title: String,
    val message: String
)
