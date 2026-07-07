package com.samfont.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrivilege()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = uiState.screen is Screen.Detail) {
        viewModel.goHome()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val screen = uiState.screen) {
            Screen.Home -> {
                HomeScreen(
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                    uiState = uiState,
                    onImportFonts = {
                        importLauncher.launch(
                            arrayOf(
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
                        )
                    },
                    onCheckUpdate = viewModel::checkForUpdates,
                    onInstallUpdate = { viewModel.installUpdate(context) },
                    onOpenFont = viewModel::openFont
                )
            }

            is Screen.Detail -> {
                FontDetailScreen(
                    modifier = androidx.compose.ui.Modifier.padding(padding),
                    font = screen.font,
                    onBack = viewModel::goHome,
                    onApply = { viewModel.applySelectedFont(screen.font) }
                )
            }
        }
    }
}
