package com.samfont.core.font

enum class FontState {
    Imported,
    Cached,
    Generating,
    PackageGenerated,
    Installing,
    SystemInstalled,
    Applying,
    Applied,
    Failed,
    Broken
}
