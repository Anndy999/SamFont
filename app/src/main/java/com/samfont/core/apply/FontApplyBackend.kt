package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel

interface FontApplyBackend {
    fun getCurrentFont(): String
    fun dryRun(fontFamily: FontFamilyModel): Boolean
    fun apply(fontFamily: FontFamilyModel): Boolean
    fun rollback(): Boolean
}
