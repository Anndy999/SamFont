package com.samfont.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samfont.BuildConfig
import com.samfont.core.apply.FontApplyDryRun
import com.samfont.core.apply.ShizukuFontApplyBackend
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.font.FontState
import com.samfont.core.privilege.PrivilegeChecker
import com.samfont.core.privilege.PrivilegeStatus
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
    val screen: Screen,
    val updateState: UpdateState,
    val currentVersionName: String
) {
    val installedFonts: List<FontFamilyModel>
        get() = fonts.filter { it.state == FontState.Installed || it.state == FontState.Applied }

    val availableFonts: List<FontFamilyModel>
        get() = fonts.filter { it.state == FontState.Imported || it.state == FontState.Available }

    val appliedFont: FontFamilyModel?
        get() = fonts.firstOrNull { it.state == FontState.Applied }
}

sealed class Screen {
    data object Main : Screen()
    data class Detail(val font: FontFamilyModel) : Screen()
}

sealed interface UiEvent {
    data class Snackbar(val message: String) : UiEvent
}

class SamFontViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        SamFontUiState(
            privilegeStatus = PrivilegeChecker.check(application.applicationContext),
            currentFontName = "Samsung Default",
            fonts = emptyList(),
            selectedTab = MainTab.Installed,
            screen = Screen.Main,
            updateState = UpdateState.Idle,
            currentVersionName = BuildConfig.VERSION_NAME
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
        _uiState.update { it.copy(selectedTab = tab, screen = Screen.Main) }
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

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(updateState = UpdateState.Checking) }
            val result = UpdateRepository.checkUpdate()
            _uiState.update { current -> current.copy(updateState = result) }
        }
    }

    fun openFont(font: FontFamilyModel) {
        _uiState.update { current -> current.copy(screen = Screen.Detail(font)) }
    }

    fun goMain() {
        _uiState.update { current -> current.copy(screen = Screen.Main) }
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
                    current.copy(screen = Screen.Detail(first), selectedTab = MainTab.Available)
                }
                emitMessage("已添加到预览，确认后再安装")
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
            FontState.Available,
            FontState.Imported -> installSelectedFont(font)
            FontState.Installed -> applySelectedFont(font)
            FontState.Applied -> viewModelScope.launch { emitMessage("当前已应用该字体") }
            FontState.Broken -> viewModelScope.launch { emitMessage("字体不可用") }
        }
    }

    private fun installSelectedFont(font: FontFamilyModel) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val result = withContext(Dispatchers.IO) {
                FontRepository.installFont(context, font)
            }

            when (result) {
                is FontRepository.InstallResult.Success -> {
                    refreshFonts()
                    _uiState.update { current ->
                        current.copy(
                            selectedTab = MainTab.Installed,
                            screen = Screen.Detail(result.font)
                        )
                    }
                    emitMessage(if (result.duplicate) "字体已安装，可应用" else "已安装，可应用")
                }
                is FontRepository.InstallResult.Failure -> emitMessage(result.message)
            }
        }
    }

    private fun applySelectedFont(font: FontFamilyModel) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val status = PrivilegeChecker.check(context)
            _uiState.update { current -> current.copy(privilegeStatus = status) }

            val targetHash = font.files.firstOrNull()?.sha256
            if (targetHash.isNullOrBlank()) {
                emitMessage("字体文件不可用")
                return@launch
            }
            if (!status.canApplySystemFont) {
                emitMessage("Shizuku 未连接、未授权或 UID 不是 1000，不能应用")
                return@launch
            }

            val installedFile = FontRepository.findInstalledFile(context, targetHash)
            val backend = ShizukuFontApplyBackend(status)
            val plan = backend.createPlan(
                fontFamily = font,
                currentHash = FontRepository.readAppliedHash(context),
                installedExists = installedFile != null
            )

            if (plan.alreadyApplied) {
                emitMessage("当前已应用")
                return@launch
            }

            val dryRun = FontApplyDryRun.run(context, font, status)
            if (!dryRun.canProceedToApply) {
                emitMessage("应用前检查失败：${dryRun.checks.joinToString(" / ")}")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                backend.apply(plan, font)
            }

            if (result.success) {
                FontRepository.markApplied(context, targetHash)
                refreshFonts()
                val applied = FontRepository.fontFamilies.value.firstOrNull { it.files.firstOrNull()?.sha256 == targetHash }
                _uiState.update { current ->
                    current.copy(
                        selectedTab = MainTab.Installed,
                        screen = applied?.let { Screen.Detail(it) } ?: Screen.Main
                    )
                }
            }
            emitMessage(result.message)
        }
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(UiEvent.Snackbar(message))
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
