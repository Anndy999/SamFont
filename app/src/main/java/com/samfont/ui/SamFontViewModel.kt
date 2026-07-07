package com.samfont.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samfont.BuildConfig
import com.samfont.core.apply.StubFontApplyBackend
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontRepository
import com.samfont.core.privilege.PrivilegeChecker
import com.samfont.core.privilege.PrivilegeStatus
import com.samfont.core.update.GithubReleaseUpdateBackend
import com.samfont.core.update.UpdateInstaller
import com.samfont.core.update.UpdateRepository
import com.samfont.core.update.UpdateState
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SamFontUiState(
    val privilegeStatus: PrivilegeStatus,
    val currentFontName: String,
    val fonts: List<FontFamilyModel>,
    val screen: Screen,
    val updateState: UpdateState,
    val currentVersionName: String
)

sealed class Screen {
    data object Home : Screen()
    data class Detail(val font: FontFamilyModel) : Screen()
}

sealed interface UiEvent {
    data class Snackbar(val message: String) : UiEvent
}

class SamFontViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = StubFontApplyBackend()
    private val _uiState = MutableStateFlow(
        SamFontUiState(
            privilegeStatus = PrivilegeChecker.check(),
            currentFontName = backend.getCurrentFont(),
            fonts = emptyList(),
            screen = Screen.Home,
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

    fun refreshFonts() {
        FontRepository.reload(getApplication<Application>().applicationContext)
        _uiState.update { current ->
            current.copy(fonts = FontRepository.fontFamilies.value)
        }
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

    fun goHome() {
        _uiState.update { current -> current.copy(screen = Screen.Home) }
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
                    UpdateInstaller.downloadApk(
                        context = context.applicationContext,
                        info = updateState.info
                    ) { downloaded, total ->
                        val progress = if (total != null && total > 0) {
                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        _uiState.update { current ->
                            current.copy(
                                updateState = UpdateState.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            )
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
                emitMessage("已取消字体导入")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                var importedCount = 0
                val errors = mutableListOf<String>()

                uris.forEach { uri ->
                    runCatching {
                        copyFontToPrivateDir(context.applicationContext, uri)
                        importedCount += 1
                    }.onFailure { throwable ->
                        errors += throwable.message ?: "导入字体失败"
                    }
                }

                FontRepository.reload(context.applicationContext)
                importedCount to errors
            }

            val importedCount = result.first
            val errors = result.second

            when {
                importedCount > 0 && errors.isEmpty() -> emitMessage("已导入 $importedCount 个字体")
                importedCount > 0 -> emitMessage("已导入 $importedCount 个字体，${errors.size} 个失败")
                else -> emitMessage(errors.firstOrNull() ?: "没有可导入的字体文件")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun applySelectedFont(font: FontFamilyModel) {
        // 当前阶段只发出占位提示，不触碰系统级字体修改逻辑。
        viewModelScope.launch {
            emitMessage("系统字体应用服务尚未接入")
        }
    }

    private suspend fun emitMessage(message: String) {
        _events.emit(UiEvent.Snackbar(message))
    }

    private fun copyFontToPrivateDir(context: Context, uri: Uri) {
        val displayName = queryDisplayName(context, uri)
            ?: throw IllegalArgumentException("无法识别字体文件名称")

        if (!FontRepository.isSupportedFontFile(displayName)) {
            throw IllegalArgumentException("仅支持 .ttf / .otf / .ttc 字体文件")
        }

        val fontDir = File(context.filesDir, "fonts")
        if (!fontDir.exists() && !fontDir.mkdirs()) {
            throw IOException("无法创建字体目录")
        }

        val extension = displayName.substringAfterLast('.', "")
        val safeName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
        val targetFile = File(fontDir, safeName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法读取选择的字体文件")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            } ?: uri.lastPathSegment
    }
}
