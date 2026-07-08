package com.samfont.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samfont.BuildConfig
import com.samfont.core.apply.FontApplyDryRun
import com.samfont.core.apply.SamsungFontPackageBackend
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.font.FontState
import com.samfont.core.privilege.PrivilegeChecker
import com.samfont.core.privilege.PrivilegeStatus
import com.samfont.core.samsung.SamsungFontApplyMode
import com.samfont.core.shizuku.ShizukuBridge
import com.samfont.core.update.UpdateInstaller
import com.samfont.core.update.UpdateRepository
import com.samfont.core.update.UpdateState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab {
    Installed,
    Available,
    About,
    Search
}

data class SamFontUiState(
    val privilegeStatus: PrivilegeStatus,
    val currentFontName: String,
    val fonts: List<FontFamilyModel>,
    val selectedTab: MainTab,
    val selectedFontSheet: FontFamilyModel?,
    val updateState: UpdateState,
    val currentVersionName: String,
    val latestBackendLog: String,
    val applyMode: SamsungFontApplyMode
) {
    val installedFonts: List<FontFamilyModel>
        get() = fonts.filter { it.state == FontState.SystemInstalled || it.state == FontState.Applied }

    val availableFonts: List<FontFamilyModel>
        get() = fonts.filter {
            it.state == FontState.Imported ||
                it.state == FontState.Cached ||
                it.state == FontState.PackageGenerated ||
                it.state == FontState.Failed
        }

    val appliedFont: FontFamilyModel?
        get() = fonts.firstOrNull { it.state == FontState.Applied }
}

sealed interface UiEvent {
    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: SnackbarAction = SnackbarAction.None
    ) : UiEvent
}

enum class SnackbarAction {
    None,
    CopyInstallLog
}

class SamFontViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        SamFontUiState(
            privilegeStatus = PrivilegeChecker.check(application.applicationContext),
            currentFontName = "Samsung Default",
            fonts = emptyList(),
            selectedTab = MainTab.Installed,
            selectedFontSheet = null,
            updateState = UpdateState.Idle,
            currentVersionName = BuildConfig.VERSION_NAME,
            latestBackendLog = "",
            applyMode = SamsungFontApplyMode.Auto
        )
    )
    val uiState: StateFlow<SamFontUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events

    init {
        refreshFonts()
        checkForUpdates()
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedFontSheet = null) }
    }

    fun refreshFonts() {
        val context = getApplication<Application>().applicationContext
        FontRepository.reload(context)
        _uiState.update { current ->
            val fonts = FontRepository.fontFamilies.value
            current.copy(
                fonts = fonts,
                currentFontName = fonts.firstOrNull { it.state == FontState.Applied }?.displayName ?: "Samsung Default"
            )
        }
    }

    fun refreshPrivilege() {
        val context = getApplication<Application>().applicationContext
        _uiState.update { current ->
            current.copy(privilegeStatus = PrivilegeChecker.check(context))
        }
    }

    fun requestShizukuPermission() {
        runCatching {
            ShizukuBridge.requestPermission()
        }.onFailure {
            viewModelScope.launch { emitMessage(it.message ?: "Shizuku 权限请求失败") }
        }
        refreshPrivilege()
    }

    fun copyInstallLog(context: Context) {
        val log = _uiState.value.latestBackendLog
        if (log.isBlank()) {
            viewModelScope.launch { emitMessage("暂无安装日志") }
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SamFont install log", log))
        viewModelScope.launch { emitMessage("安装日志已复制") }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(updateState = UpdateState.Checking) }
            val result = UpdateRepository.checkUpdate()
            _uiState.update { current -> current.copy(updateState = result) }
        }
    }

    fun openFont(font: FontFamilyModel) {
        _uiState.update { current -> current.copy(selectedFontSheet = font) }
    }

    fun dismissFontSheet() {
        _uiState.update { current -> current.copy(selectedFontSheet = null) }
    }

    fun setApplyMode(mode: SamsungFontApplyMode) {
        _uiState.update { current -> current.copy(applyMode = mode) }
    }

    fun installUpdate(context: Context) {
        val updateState = _uiState.value.updateState
        if (updateState !is UpdateState.Available) {
            viewModelScope.launch { emitMessage("没有可安装的更新包") }
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(updateState = UpdateState.Downloading(0, 0, null)) }
            runCatching {
                val downloadResult = withContext(Dispatchers.IO) {
                    UpdateInstaller.downloadApk(context.applicationContext, updateState.info) { downloaded, total ->
                        val progress = if (total != null && total > 0) {
                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        _uiState.update { current ->
                            current.copy(updateState = UpdateState.Downloading(progress, downloaded, total))
                        }
                    }
                }
                _uiState.update { current -> current.copy(updateState = UpdateState.Installing) }
                context.startActivity(UpdateInstaller.buildInstallIntent(downloadResult.uri))
                emitMessage("已打开系统安装界面")
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(updateState = UpdateState.Error(throwable.message ?: "下载更新失败"))
                }
                emitMessage(throwable.message ?: "下载更新失败")
            }
        }
    }

    fun importFonts(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            if (uris.isEmpty()) {
                emitMessage("已取消字体选择")
                return@launch
            }

            val imported = mutableListOf<FontFamilyModel>()
            val errors = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    when (val result = copyFontToImportedDir(context.applicationContext, uri)) {
                        is FontRepository.ImportResult.Success -> imported += result.font
                        is FontRepository.ImportResult.Failure -> errors += result.message
                    }
                }
                FontRepository.reload(context.applicationContext)
            }

            refreshFonts()
            val first = imported.firstOrNull()
            if (first != null) {
                _uiState.update { current ->
                    current.copy(selectedFontSheet = first, selectedTab = MainTab.Available)
                }
                emitMessage("已添加到预览，点击 Generate Fonts 安装字体包")
            } else {
                emitMessage(errors.firstOrNull() ?: "没有可预览的字体文件")
            }
        }
    }

    fun importFolder(context: Context, treeUri: Uri?) {
        viewModelScope.launch {
            if (treeUri == null) {
                emitMessage("已取消选择文件夹")
                return@launch
            }
            val folder = DocumentFile.fromTreeUri(context, treeUri)
            val fontUris = folder
                ?.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { it.uri }
                .orEmpty()
            importFonts(context, fontUris)
        }
    }

    fun handleFontPrimaryAction(font: FontFamilyModel) {
        when (font.state) {
            FontState.Imported,
            FontState.Cached,
            FontState.PackageGenerated,
            FontState.Failed -> installSamsungFontPackage(font)
            FontState.SystemInstalled -> installSamsungFontPackage(font)
            FontState.Applied -> viewModelScope.launch { emitMessage("当前已应用该字体") }
            FontState.Generating,
            FontState.Installing,
            FontState.Applying,
            FontState.Broken -> viewModelScope.launch { emitMessage("当前状态不可操作") }
        }
    }

    private fun installSamsungFontPackage(font: FontFamilyModel) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val status = PrivilegeChecker.check(context)
            _uiState.update { current -> current.copy(privilegeStatus = status) }

            val shizuku = status.shizukuStatus
            val shizukuLog = buildString {
                appendLine("Shizuku available=${shizuku?.available ?: false}")
                appendLine("Shizuku permissionGranted=${shizuku?.permissionGranted ?: false}")
                appendLine("Shizuku uid=${shizuku?.uid ?: "unknown"}")
                appendLine("Shizuku source=${shizuku?.source ?: "unknown"}")
            }
            val privilegeError = when {
                shizuku?.available != true -> "Shizuku 未运行，请先启动 Shizuku。"
                !shizuku.permissionGranted -> "Shizuku 未授权，无法安装字体包。"
                shizuku.uid != 1000 -> "需要 Shizuku UID1000 权限才能安装 Samsung 字体包。"
                else -> null
            }
            if (privilegeError != null) {
                _uiState.update { current -> current.copy(latestBackendLog = shizukuLog) }
                emitMessage(privilegeError)
                return@launch
            }

            val dryRun = FontApplyDryRun.run(context, font, status)
            if (!dryRun.canProceedToApply) {
                _uiState.update { current ->
                    current.copy(latestBackendLog = dryRun.checks.joinToString("\n"))
                }
                emitMessage("安装前检查失败：${dryRun.checks.joinToString(" / ")}")
                return@launch
            }

            val targetHash = font.files.firstOrNull()?.sha256.orEmpty()
            val backend = SamsungFontPackageBackend(
                context = context,
                privilegeStatus = status,
                applyMode = _uiState.value.applyMode
            )
            val plan = backend.createPlan(
                fontFamily = font,
                currentHash = FontRepository.readAppliedHash(context),
                installedExists = FontRepository.findInstalledFile(context, targetHash) != null
            )

            val result = withContext(Dispatchers.IO) {
                backend.apply(plan, font)
            }
            _uiState.update { current -> current.copy(latestBackendLog = result.backendLog.orEmpty()) }
            if (result.success) {
                val cached = withContext(Dispatchers.IO) { FontRepository.installFont(context, font) }
                if (result.applied && targetHash.isNotBlank()) {
                    withContext(Dispatchers.IO) { FontRepository.markApplied(context, targetHash) }
                }
                refreshFonts()
                val updated = when (cached) {
                    is FontRepository.InstallResult.Success -> cached.font
                    is FontRepository.InstallResult.Failure -> null
                }
                val selected = FontRepository.fontFamilies.value.firstOrNull { family ->
                    family.files.firstOrNull()?.sha256 == targetHash
                } ?: updated
                _uiState.update { current ->
                    current.copy(
                        selectedTab = MainTab.Installed,
                        selectedFontSheet = selected ?: current.selectedFontSheet
                    )
                }
            }
            if (result.success) {
                emitMessage(result.message)
            } else {
                emitMessage(
                    message = "字体包安装失败",
                    actionLabel = "复制日志",
                    action = SnackbarAction.CopyInstallLog
                )
            }
        }
    }

    private suspend fun emitMessage(
        message: String,
        actionLabel: String? = null,
        action: SnackbarAction = SnackbarAction.None
    ) {
        _events.emit(UiEvent.Snackbar(message, actionLabel, action))
    }

    private fun copyFontToImportedDir(context: Context, uri: Uri): FontRepository.ImportResult {
        val displayName = queryDisplayName(context, uri)
            ?: uri.lastPathSegment
            ?: "imported-font"
        val tempFile = File(context.cacheDir, "font-import-${System.nanoTime()}.tmp")

        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法读取选择的字体文件")

        return try {
            FontRepository.importFontFile(context, tempFile, displayName)
        } finally {
            tempFile.delete()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment
    }
}
