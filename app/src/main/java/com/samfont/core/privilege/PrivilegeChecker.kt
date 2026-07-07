package com.samfont.core.privilege

import android.os.Process
import android.os.UserHandle
import java.io.File

object PrivilegeChecker {
    private const val PER_USER_RANGE = 100000

    fun check(): PrivilegeStatus {
        val processUid = Process.myUid()
        val procStatus = readProcStatusUids()

        // Android 多用户环境下，UID 由 userId 和 appId 组合而成。
        // 优先使用 UserHandle.getAppId(uid) 的语义；公开 SDK 不稳定时再回退到 PER_USER_RANGE 拆分。
        val candidates = buildList {
            add(UidCandidate(processUid, "Process.myUid()"))
            procStatus?.let {
                add(UidCandidate(it.realUid, "/proc/self/status real"))
                add(UidCandidate(it.effectiveUid, "/proc/self/status effective"))
                add(UidCandidate(it.savedSetUid, "/proc/self/status saved"))
                add(UidCandidate(it.fileSystemUid, "/proc/self/status fs"))
            }
        }.distinctBy { it.uid }

        val matched = candidates.firstOrNull { getAppIdCompat(it.uid) == Process.SYSTEM_UID }
        val selected = matched ?: candidates.first()
        val appId = getAppIdCompat(selected.uid)
        val isUid1000 = appId == Process.SYSTEM_UID
        val effectiveUid = procStatus?.effectiveUid ?: processUid

        return if (isUid1000) {
            PrivilegeStatus(
                uid = selected.uid,
                appId = appId,
                isUid1000 = true,
                canApplySystemFont = true,
                title = "UID1000 权限已启用",
                message = "当前环境支持系统字体应用",
                processUid = processUid,
                effectiveUid = effectiveUid,
                detectionSource = selected.source
            )
        } else {
            PrivilegeStatus(
                uid = selected.uid,
                appId = appId,
                isUid1000 = false,
                canApplySystemFont = false,
                title = "需要 UID1000 权限",
                message = "当前环境仅支持字体预览，无法应用系统字体",
                processUid = processUid,
                effectiveUid = effectiveUid,
                detectionSource = selected.source
            )
        }
    }

    private fun getAppIdCompat(uid: Int): Int {
        return runCatching {
            // 部分编译环境不能直接调用 UserHandle.getAppId；这里用反射保持同等语义。
            val method = UserHandle::class.java.getDeclaredMethod(
                "getAppId",
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(null, uid) as Int
        }.getOrElse {
            uid % PER_USER_RANGE
        }
    }

    private fun readProcStatusUids(): ProcStatusUids? {
        return runCatching {
            val uidLine = File("/proc/self/status")
                .readLines()
                .firstOrNull { it.startsWith("Uid:") }
                ?: return@runCatching null

            val values = uidLine
                .removePrefix("Uid:")
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.toIntOrNull() }

            if (values.size < 4) {
                null
            } else {
                ProcStatusUids(
                    realUid = values[0],
                    effectiveUid = values[1],
                    savedSetUid = values[2],
                    fileSystemUid = values[3]
                )
            }
        }.getOrNull()
    }

    private data class UidCandidate(
        val uid: Int,
        val source: String
    )

    private data class ProcStatusUids(
        val realUid: Int,
        val effectiveUid: Int,
        val savedSetUid: Int,
        val fileSystemUid: Int
    )
}
