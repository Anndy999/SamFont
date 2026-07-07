package com.samfont.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samfont.core.update.UpdateState

@Composable
fun UpdateCard(
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OTA 更新",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            when (updateState) {
                UpdateState.Idle -> {
                    Text(
                        text = "尚未检查更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UpdateState.Checking -> {
                    Text(
                        text = "正在检查 GitHub Releases...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UpdateState.Unconfigured -> {
                    Text(
                        text = "请先配置 GitHub 仓库地址，OTA 才能工作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateState.UpToDate -> {
                    Text(
                        text = "当前已是最新版本：${updateState.currentVersionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateState.Available -> {
                    Text(
                        text = "发现新版本：${updateState.info.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = updateState.info.body.ifBlank { "暂无更新说明" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateState.Error -> {
                    Text(
                        text = updateState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UpdateState.Downloading -> {
                    Text(
                        text = "正在下载更新包...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                UpdateState.Installing -> {
                    Text(
                        text = "正在打开安装界面...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCheckUpdate) {
                    Text(text = "检查更新")
                }

                if (updateState is UpdateState.Available) {
                    Button(onClick = onInstallUpdate) {
                        Text(text = "下载并安装")
                    }
                }
            }
        }
    }
}
