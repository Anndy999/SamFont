package com.samfont.core.privilege

import android.os.Process

object PrivilegeChecker {
    fun check(): PrivilegeStatus {
        val uid = Process.myUid()
        // Android 多用户环境下，UID 里混合了 userId 和 appId；这里按 PER_USER_RANGE 拆出 appId。
        // 这和 UserHandle.getAppId(uid) 的语义一致，只是当前 SDK 里该方法不可直接引用。
        val appId = uid % 100000
        val isUid1000 = appId == Process.SYSTEM_UID

        return if (isUid1000) {
            PrivilegeStatus(
                uid = uid,
                appId = appId,
                isUid1000 = true,
                canApplySystemFont = true,
                title = "UID1000 权限已启用",
                message = "当前环境支持系统字体应用"
            )
        } else {
            PrivilegeStatus(
                uid = uid,
                appId = appId,
                isUid1000 = false,
                canApplySystemFont = false,
                title = "需要 UID1000 权限",
                message = "当前环境仅支持字体预览，无法应用系统字体"
            )
        }
    }
}
