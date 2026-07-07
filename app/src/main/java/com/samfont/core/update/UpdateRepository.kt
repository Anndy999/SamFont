package com.samfont.core.update

import android.content.Context
import com.samfont.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateRepository {
    suspend fun checkUpdate(): UpdateState = withContext(Dispatchers.IO) {
        if (!GithubReleaseUpdateBackend.isConfigured()) {
            return@withContext UpdateState.Unconfigured
        }

        runCatching {
            val latest = GithubReleaseUpdateBackend.fetchLatest()
            if (latest.versionCode > BuildConfig.VERSION_CODE) {
                UpdateState.Available(latest)
            } else {
                UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            }
        }.getOrElse { throwable ->
            UpdateState.Error(throwable.message ?: "更新检查失败")
        }
    }
}
