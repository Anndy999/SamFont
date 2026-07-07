package com.samfont.core.privilege

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.system.Os
import com.samfont.BuildConfig
import com.samfont.core.shizuku.ShizukuBridge
import java.io.File

object PrivilegeChecker {
    private const val PER_USER_RANGE = 100000

    fun check(context: Context? = null): PrivilegeStatus {
        val diagnostics = mutableListOf<String>()
        val processUid = Process.myUid()
        val osUid = runCatching { Os.getuid() }
            .onFailure { diagnostics += "Os.getuid() 读取失败：${it.message}" }
            .getOrDefault(processUid)
        val effectiveUid = runCatching { Os.geteuid() }
            .onFailure { diagnostics += "Os.geteuid() 读取失败：${it.message}" }
            .getOrDefault(osUid)
        val packageUid = context?.let { appContext ->
            runCatching {
                appContext.packageManager.getApplicationInfo(appContext.packageName, 0).uid
            }.onFailure {
                diagnostics += "PackageManager.applicationInfo.uid 读取失败：${it.message}"
            }.getOrNull()
        }
        val procStatus = readProcStatusUids(diagnostics)
        val selinuxContext = readSelinuxContext(diagnostics)
        val shizukuStatus = ShizukuBridge.check()

        // Android 多用户环境下，UID 由 userId 和 appId 组合而成。
        // 判断 UID1000 时既比较原始 uid，也比较 UserHandle.getAppId(uid) 的结果。
        val candidates = buildList {
            add(UidCandidate(processUid, "Process.myUid()"))
            add(UidCandidate(osUid, "Os.getuid()"))
            add(UidCandidate(effectiveUid, "Os.geteuid()"))
            packageUid?.let { add(UidCandidate(it, "PackageManager.applicationInfo.uid")) }
            procStatus?.let {
                add(UidCandidate(it.realUid, "/proc/self/status real"))
                add(UidCandidate(it.effectiveUid, "/proc/self/status effective"))
                add(UidCandidate(it.savedSetUid, "/proc/self/status saved"))
                add(UidCandidate(it.fileSystemUid, "/proc/self/status fs"))
            }
        }.distinctBy { "${it.source}:${it.uid}" }

        candidates.forEach { candidate ->
            diagnostics += "${candidate.source}: uid=${candidate.uid}, appId=${getAppIdCompat(candidate.uid)}"
        }
        selinuxContext?.let { diagnostics += "SELinux: $it" }
        diagnostics += "Shizuku: uid=${shizukuStatus.uid ?: "未知"}, source=${shizukuStatus.source}"

        val matched = candidates.firstOrNull { isSystemUidCandidate(it.uid) }
        val selected = matched ?: candidates.first()
        val appId = getAppIdCompat(selected.uid)
        val isUid1000 = matched != null

        return if (isUid1000) {
            PrivilegeStatus(
                uid = selected.uid,
                appId = appId,
                isUid1000 = true,
                canApplySystemFont = true,
                title = "UID1000 权限已启用",
                message = "当前环境支持系统字体应用",
                installMode = BuildConfig.INSTALL_MODE,
                processUid = processUid,
                osUid = osUid,
                effectiveUid = effectiveUid,
                packageUid = packageUid,
                realUid = procStatus?.realUid,
                savedSetUid = procStatus?.savedSetUid,
                fileSystemUid = procStatus?.fileSystemUid,
                selinuxContext = selinuxContext,
                shizukuStatus = shizukuStatus,
                diagnostics = diagnostics,
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
                installMode = BuildConfig.INSTALL_MODE,
                processUid = processUid,
                osUid = osUid,
                effectiveUid = effectiveUid,
                packageUid = packageUid,
                realUid = procStatus?.realUid,
                savedSetUid = procStatus?.savedSetUid,
                fileSystemUid = procStatus?.fileSystemUid,
                selinuxContext = selinuxContext,
                shizukuStatus = shizukuStatus,
                diagnostics = diagnostics,
                detectionSource = selected.source
            )
        }
    }

    private fun isSystemUidCandidate(uid: Int): Boolean {
        return uid == Process.SYSTEM_UID || getAppIdCompat(uid) == Process.SYSTEM_UID
    }

    private fun getAppIdCompat(uid: Int): Int {
        return runCatching {
            // 反射调用保持 UserHandle.getAppId(uid) 语义；失败时按 Android PER_USER_RANGE 回退。
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

    private fun readProcStatusUids(diagnostics: MutableList<String>): ProcStatusUids? {
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
                diagnostics += "/proc/self/status Uid 字段格式异常：$uidLine"
                null
            } else {
                ProcStatusUids(
                    realUid = values[0],
                    effectiveUid = values[1],
                    savedSetUid = values[2],
                    fileSystemUid = values[3]
                )
            }
        }.onFailure {
            diagnostics += "/proc/self/status 读取失败：${it.message}"
        }.getOrNull()
    }

    private fun readSelinuxContext(diagnostics: MutableList<String>): String? {
        return runCatching {
            File("/proc/self/attr/current")
                .readText()
                .trim()
                .takeIf { it.isNotBlank() }
        }.onFailure {
            diagnostics += "/proc/self/attr/current 读取失败：${it.message}"
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
