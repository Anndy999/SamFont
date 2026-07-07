package com.samfont.core.update

data class UpdateInfo(
    val tagName: String,
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val body: String,
    val apkUrl: String,
    val publishedAt: String? = null
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object Unconfigured : UpdateState()
    data class UpToDate(val currentVersionName: String) : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
    data object Downloading : UpdateState()
    data object Installing : UpdateState()
}
