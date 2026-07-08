package com.samfont.core.apply

import com.samfont.core.font.FontFamilyModel

interface FontApplyBackend {
    fun getCurrentFont(): String
    fun createPlan(fontFamily: FontFamilyModel, currentHash: String?, installedExists: Boolean): FontApplyPlan
    suspend fun apply(plan: FontApplyPlan, fontFamily: FontFamilyModel): FontApplyResult
    fun rollback(): FontApplyResult
}
