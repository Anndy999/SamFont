package com.samfont.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.preview.FontPreviewEngine
import androidx.compose.ui.text.TextStyle
import java.io.File

@Composable
fun FontDetailScreen(
    modifier: Modifier = Modifier,
    font: FontFamilyModel,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    var selectedWeight by rememberSaveable(font.id) {
        mutableIntStateOf(font.supportedWeights.firstOrNull() ?: 400)
    }

    val previewFile = font.files.firstOrNull()
    val previewFileExists = remember(previewFile?.path) {
        previewFile?.path?.let { File(it).exists() } ?: false
    }
    val typeface = remember(previewFile?.path, selectedWeight) {
        previewFile?.let { FontPreviewEngine.loadTypeface(it, selectedWeight) }
    }
    val previewFontFamily = remember(typeface) {
        typeface?.let { FontFamily(it) } ?: FontFamily.Default
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text(text = "返回")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (font.isVariableFont) "Variable Font 结构已预留" else "本地字体预览",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "字重预览：$selectedWeight",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = selectedWeight.toFloat(),
                    onValueChange = { value ->
                        selectedWeight = value
                            .toInt()
                            .coerceIn(100, 900)
                    },
                    valueRange = 100f..900f,
                    steps = 7
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(100, 300, 500, 700, 900).forEach { weight ->
                        Text(
                            text = weight.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "字体预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (previewFile == null) {
                    Text(
                        text = "字体文件不存在，已回退系统字体",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!previewFileExists) {
                    Text(
                        text = "字体文件不存在，已回退系统字体",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (typeface == null) {
                    Text(
                        text = "Android 预览引擎无法加载该字体，当前预览已回退系统字体；文件仍保留在字体库。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val previewTexts = listOf(
                    "山高月小，水落石出",
                    "The quick brown fox jumps over the lazy dog",
                    "1234567890",
                    "符号：，。！？@#￥%&*"
                )

                previewTexts.forEach { text ->
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = previewFontFamily,
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            fontWeight = if (selectedWeight in 100..900) {
                                FontWeight(selectedWeight)
                            } else {
                                FontWeight.Normal
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onApply
        ) {
            Text(text = "尝试应用字体")
        }
    }
}
