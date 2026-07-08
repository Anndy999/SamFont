package com.samfont.core.shizuku

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val command: String
) {
    val success: Boolean
        get() = exitCode == 0
}
