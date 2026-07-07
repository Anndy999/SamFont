package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel

class StubFontApplyBackend : FontApplyBackend {
    override fun getCurrentFont(): String = "Samsung Default"

    override fun dryRun(fontFamily: FontFamilyModel): Boolean = true

    override fun apply(fontFamily: FontFamilyModel): Boolean {
        // 当前阶段只保留接口，不做任何系统级字体修改，避免引入危险操作。
        return false
    }

    override fun rollback(): Boolean = true
}
