package com.samfont.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samfont.theme.SamFontColors
import com.samfont.theme.SamFontDimens

@Composable
fun SamFontApp(viewModel: SamFontViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.importFonts(context, uris)
    }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.importFolder(context, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = if (event.actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed && event.action == SnackbarAction.CopyInstallLog) {
                        viewModel.copyInstallLog(context)
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrivilege()
                viewModel.refreshFonts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = uiState.selectedFontSheet != null) {
        viewModel.dismissFontSheet()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SamFontColors.Background,
        bottomBar = {
            SamFontBottomBar(
                selectedTab = uiState.selectedTab,
                onSelectTab = viewModel::selectTab
            )
        }
    ) { padding ->
        HomeScreen(
            modifier = Modifier.padding(padding),
            uiState = uiState,
            onImportFonts = { importLauncher.launch(fontMimeTypes()) },
            onImportFolder = { folderLauncher.launch(null) },
            onRequestShizukuPermission = viewModel::requestShizukuPermission,
            onCheckUpdate = viewModel::checkForUpdates,
            onInstallUpdate = { viewModel.installUpdate(context) },
            onCopyInstallLog = { viewModel.copyInstallLog(context) },
            onOpenFont = viewModel::openFont
        )

        uiState.selectedFontSheet?.let { font ->
            Dialog(
                onDismissRequest = viewModel::dismissFontSheet,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.88f),
                    shape = RoundedCornerShape(SamFontDimens.SheetRadius),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    FontActionSheet(
                        font = font,
                        canApplySystemFont = uiState.privilegeStatus.canApplySystemFont,
                        applyMode = uiState.applyMode,
                        onApplyModeChange = viewModel::setApplyMode,
                        onCancel = viewModel::dismissFontSheet,
                        onPrimaryAction = { viewModel.handleFontPrimaryAction(font) }
                    )
                }
            }
        }
    }
}

private fun fontMimeTypes(): Array<String> = arrayOf(
    "font/ttf",
    "font/otf",
    "font/ttc",
    "font/sfnt",
    "font/collection",
    "application/font-sfnt",
    "application/x-font-ttf",
    "application/x-font-truetype",
    "application/x-font-otf",
    "application/x-font-opentype",
    "application/x-font-ttc",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*"
)
