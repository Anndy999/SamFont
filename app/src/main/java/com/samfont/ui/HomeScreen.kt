package com.samfont.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samfont.core.font.FontFamilyModel
import com.samfont.theme.SamFontColors
import com.samfont.theme.SamFontDimens

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    uiState: SamFontUiState,
    onImportFonts: () -> Unit,
    onImportFolder: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCopyInstallLog: () -> Unit,
    onOpenFont: (FontFamilyModel) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SamsungHeader(active = uiState.privilegeStatus.canApplySystemFont) }

        when (uiState.selectedTab) {
            MainTab.Installed -> {
                item {
                    Text(
                        text = "${uiState.installedFonts.size} fonts installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SamFontColors.TextSecondary
                    )
                }
                item { Text(text = "Applied", style = MaterialTheme.typography.titleLarge) }
                item { AppliedFontCard(font = uiState.appliedFont, onOpenFont = onOpenFont) }
                item { Text(text = "All Fonts", style = MaterialTheme.typography.titleLarge) }
                item { DefaultFontCard(active = uiState.appliedFont == null) }
                items(uiState.installedFonts, key = FontFamilyModel::id) { font ->
                    FontCard(font = font, onClick = { onOpenFont(font) })
                }
            }

            MainTab.Available -> {
                item {
                    AddFontActionCard(
                        title = "Add font from storage",
                        subtitle = "Browse for .ttf or .otf file",
                        leading = "+",
                        onClick = onImportFonts
                    )
                }
                item {
                    AddFontActionCard(
                        title = "Add fonts from folder",
                        subtitle = "Pick a folder - all .ttf/.otf files load at once",
                        leading = "↓",
                        onClick = onImportFolder
                    )
                }

                if (uiState.availableFonts.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(SamFontDimens.CardRadius),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "No fonts yet.", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Tap above to add a .ttf file.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SamFontColors.TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    item { Text(text = "Ready to generate", style = MaterialTheme.typography.titleLarge) }
                    items(uiState.availableFonts, key = FontFamilyModel::id) { font ->
                        FontCard(font = font, onClick = { onOpenFont(font) })
                    }
                }
            }

            MainTab.About -> {
                item {
                    AboutPanel(
                        uiState = uiState,
                        onRequestShizukuPermission = onRequestShizukuPermission,
                        onCheckUpdate = onCheckUpdate,
                        onInstallUpdate = onInstallUpdate,
                        onCopyInstallLog = onCopyInstallLog
                    )
                }
            }

            MainTab.Search -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(SamFontDimens.CardRadius),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Search", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = "Search will be added later.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SamFontColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungHeader(active: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Samsung",
            style = MaterialTheme.typography.titleLarge,
            color = SamFontColors.PrimaryBlueDark
        )
        Text(
            text = "Fonts",
            style = MaterialTheme.typography.displayMedium,
            fontSize = 54.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            shape = RoundedCornerShape(SamFontDimens.PillRadius),
            color = if (active) SamFontColors.ActiveBlueContainer else SamFontColors.ErrorContainer
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                text = if (active) "● Active" else "● Not authorized",
                color = if (active) SamFontColors.PrimaryBlueDark else SamFontColors.Error,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun AddFontActionCard(
    title: String,
    subtitle: String,
    leading: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SamFontDimens.CardRadius),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(SamFontDimens.IconContainerRadius),
                color = SamFontColors.ActiveBlueContainer
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    text = leading,
                    color = SamFontColors.PrimaryBlueDark,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = SamFontColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun AppliedFontCard(
    font: FontFamilyModel?,
    onOpenFont: (FontFamilyModel) -> Unit
) {
    if (font == null) {
        DefaultFontCard(active = true)
    } else {
        FontCard(font = font, onClick = { onOpenFont(font) })
    }
}

@Composable
private fun DefaultFontCard(active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SamFontDimens.CardRadius),
        color = if (active) SamFontColors.ActiveBlueContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Default", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "The quick brown fox",
                style = MaterialTheme.typography.bodyLarge,
                color = SamFontColors.TextSecondary
            )
            Text(
                text = if (active) "System / Active" else "System",
                style = MaterialTheme.typography.bodySmall,
                color = SamFontColors.PrimaryBlueDark
            )
        }
    }
}

@Composable
private fun AboutPanel(
    uiState: SamFontUiState,
    onRequestShizukuPermission: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCopyInstallLog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PrivilegeStatusCard(status = uiState.privilegeStatus)
        UpdateCard(
            updateState = uiState.updateState,
            currentVersionName = uiState.currentVersionName,
            onCheckUpdate = onCheckUpdate,
            onInstallUpdate = onInstallUpdate
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRequestShizukuPermission) {
            Text(text = "Request Shizuku Permission")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.latestBackendLog.isNotBlank(),
            onClick = onCopyInstallLog
        ) {
            Text(text = "Copy Install Log")
        }
    }
}
