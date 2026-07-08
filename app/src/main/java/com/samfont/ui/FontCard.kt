package com.samfont.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontState

@Composable
fun FontCard(
    font: FontFamilyModel,
    onClick: () -> Unit
) {
    val active = font.state == FontState.Applied
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (active) Color(0xFFDCEEFF) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = font.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "The quick brown fox",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = buildFontTags(font),
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Color(0xFF0B78D0) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildFontTags(font: FontFamilyModel): String {
    val source = if (font.state == FontState.Applied) "SamFonts / Active" else "SamFonts"
    val variable = if (font.isVariableFont) "Variable" else "Static"
    val preview = if (font.previewAvailable) "Preview" else "No preview"
    return "$source / ${font.state.name} / $variable / ${font.fileType.uppercase()} / $preview"
}
