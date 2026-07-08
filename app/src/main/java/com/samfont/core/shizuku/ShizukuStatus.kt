package com.samfont.core.shizuku

enum class ShizukuSource {
    SYSTEM_UID,
    ROOT,
    ADB_SHELL,
    DENIED,
    NOT_RUNNING,
    UNAVAILABLE,
    UNKNOWN
}

data class ShizukuStatus(
    val available: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?,
    val source: ShizukuSource,
    val message: String
) {
    val canDryRunAsRoot: Boolean
        get() = available && permissionGranted && (source == ShizukuSource.ROOT || source == ShizukuSource.SYSTEM_UID)

    val canOperateSystemFonts: Boolean
        get() = available && permissionGranted && source == ShizukuSource.SYSTEM_UID

    val canDiagnose: Boolean
        get() = available && permissionGranted && (source == ShizukuSource.ROOT || source == ShizukuSource.SYSTEM_UID || source == ShizukuSource.ADB_SHELL)
}
