package com.samfont.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.samfont.core.privilege.PrivilegeStatus

@Composable
fun PrivilegeStatusCard(status: PrivilegeStatus) {
    val containerColor = if (status.canApplySystemFont) {
        Color(0xFFE7F4EE)
    } else {
        Color(0xFFFBEAEA)
    }
    val contentColor = if (status.canApplySystemFont) {
        Color(0xFF1C6B45)
    } else {
        Color(0xFFB3261E)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "●",
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = status.title,
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = status.message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "当前 UID: ${status.uid} / AppId: ${status.appId}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "安装模式: ${status.installMode}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Package UID: ${status.packageUid ?: "未知"} / 来源: ${status.detectionSource}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Process: ${status.processUid} / OS: ${status.osUid} / EUID: ${status.effectiveUid}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Real: ${status.realUid ?: "未知"} / Saved: ${status.savedSetUid ?: "未知"} / FS: ${status.fileSystemUid ?: "未知"}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "SELinux: ${status.selinuxContext ?: "未知"}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Shizuku: ${status.shizukuStatus?.uid ?: "未知"} / ${status.shizukuStatus?.source ?: "不可用"}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Shizuku 权限: ${status.shizukuStatus?.message ?: "不可用"}",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
            if (!status.canApplySystemFont && status.installMode == "normal") {
                Text(
                    text = "诊断: normal APK 不声明 android.uid.system，不能获得 UID1000。",
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
