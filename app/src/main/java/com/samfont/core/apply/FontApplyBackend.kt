package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel

interface FontApplyBackend {
    fun getCurrentFont(): String
    fun dryRun(fontFamily: FontFamilyModel): Boolean
    fun createPlan(fontFamily: FontFamilyModel, currentHash: String?, installedExists: Boolean): FontApplyPlan
    fun applyPlan(plan: FontApplyPlan): Boolean
    fun apply(fontFamily: FontFamilyModel): Boolean
    fun rollback(): Boolean
}
