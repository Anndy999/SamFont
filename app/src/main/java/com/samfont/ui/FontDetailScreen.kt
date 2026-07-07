package com.samfont.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samfont.BuildConfig
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.variation.FontVariationAxis
import com.samfont.core.preview.FontPreviewEngine
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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
    val visibleAxes = remember(font.variationInfo) {
        font.variationInfo
            ?.axes
            ?.filter { !it.hidden || BuildConfig.DEBUG }
            .orEmpty()
    }
    var axisValues by remember(font.id) {
        mutableStateOf(visibleAxes.associate { it.tag to it.defaultValue })
    }
    val previewVariationSettings = if (visibleAxes.isEmpty()) {
        mapOf("wght" to selectedWeight.toFloat())
    } else {
        axisValues
    }
    val previewFileExists = remember(previewFile?.path) {
        previewFile?.path?.let { File(it).exists() } ?: false
    }
    val typeface = remember(previewFile?.path, selectedWeight, previewVariationSettings) {
        previewFile?.let {
            FontPreviewEngine.loadTypeface(
                fontFile = it,
                weight = selectedWeight,
                variationSettings = previewVariationSettings
            )
        }
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
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Variable Font",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        text = "The quick brown fox",
                        style = TextStyle(
                            fontFamily = previewFontFamily,
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight(selectedWeight)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                WeightSlider(
                    weight = selectedWeight,
                    onWeightChange = { selectedWeight = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "100",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "900",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "当前字重",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$selectedWeight · ${weightLabel(selectedWeight)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    )
                }

                if (visibleAxes.isNotEmpty()) {
                    AxisControls(
                        axes = visibleAxes,
                        axisValues = axisValues,
                        onAxisValueChange = { tag, value ->
                            axisValues = axisValues + (tag to value)
                            if (tag == "wght") {
                                selectedWeight = value.toInt().coerceIn(100, 900)
                            }
                        },
                        onReset = {
                            axisValues = visibleAxes.associate { it.tag to it.defaultValue }
                            axisValues["wght"]?.let { selectedWeight = it.toInt().coerceIn(100, 900) }
                        }
                    )

                    val instances = font.variationInfo?.namedInstances.orEmpty()
                    if (instances.isNotEmpty()) {
                        Text(
                            text = "Named Instances",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            instances.forEach { instance ->
                                OutlinedButton(
                                    onClick = {
                                        axisValues = axisValues + instance.coordinates
                                        instance.coordinates["wght"]?.let {
                                            selectedWeight = it.toInt().coerceIn(100, 900)
                                        }
                                    }
                                ) {
                                    Text(text = instance.name ?: "Instance ${instance.nameId}")
                                }
                            }
                        }
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

        font.compatibilityReport?.let { report ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "字体兼容报告",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "类型: ${report.fileType.uppercase()} / TTC: ${yesNo(report.isTtc)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "可变字体: ${yesNo(report.isVariableFont)} / 轴: ${report.axes.size} / Instances: ${report.namedInstances.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Android 预览: ${yesNo(report.androidPreviewAvailable)} / CJK: ${yesNo(report.hasCjkCoverage)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "简体: ${yesNo(report.hasSimplifiedChineseCoverage)} / 繁体: ${yesNo(report.hasTraditionalChineseCoverage)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "可能适合作为系统字体: ${yesNo(report.suitableForSystemFont)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AxisControls(
    axes: List<FontVariationAxis>,
    axisValues: Map<String, Float>,
    onAxisValueChange: (String, Float) -> Unit,
    onReset: () -> Unit
) {
    val standardAxes = axes.filter { it.standard }
    val customAxes = axes.filterNot { it.standard }

    Text(
        text = "标准轴",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    standardAxes.forEach { axis ->
        AxisSlider(axis, axisValues[axis.tag] ?: axis.defaultValue, onAxisValueChange)
    }

    if (customAxes.isNotEmpty()) {
        Text(
            text = "高级轴",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        customAxes.forEach { axis ->
            AxisSlider(axis, axisValues[axis.tag] ?: axis.defaultValue, onAxisValueChange)
        }
    }

    OutlinedButton(onClick = onReset) {
        Text(text = "恢复默认值")
    }
}

@Composable
private fun AxisSlider(
    axis: FontVariationAxis,
    value: Float,
    onAxisValueChange: (String, Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${axis.tag} ${axis.name ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = trimAxisValue(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(axis.minValue, axis.maxValue),
            onValueChange = { onAxisValueChange(axis.tag, it.coerceIn(axis.minValue, axis.maxValue)) },
            valueRange = axis.minValue..axis.maxValue
        )
    }
}

private fun trimAxisValue(value: Float): String {
    val intValue = value.toInt()
    return if (value == intValue.toFloat()) intValue.toString() else "%.2f".format(value)
}

private fun yesNo(value: Boolean): String = if (value) "是" else "否"

private fun weightLabel(weight: Int): String {
    return when (weight) {
        in 100..149 -> "Thin"
        in 150..249 -> "Extra Light"
        in 250..349 -> "Light"
        in 350..449 -> "Regular"
        in 450..549 -> "Medium"
        in 550..649 -> "Semi Bold"
        in 650..749 -> "Bold"
        in 750..849 -> "Extra Bold"
        else -> "Black"
    }
}

@Composable
private fun WeightSlider(
    weight: Int,
    onWeightChange: (Int) -> Unit
) {
    var widthPx by remember { mutableStateOf(0) }
    val activeColor = Color(0xFF0B84E5)
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val tickColor = MaterialTheme.colorScheme.primary
    val thumbFill = MaterialTheme.colorScheme.surface

    fun weightFromX(x: Float): Int {
        if (widthPx <= 0) {
            return weight
        }

        val min = 100
        val max = 900
        val clamped = x.coerceIn(0f, widthPx.toFloat())
        val raw = min + ((clamped / widthPx.toFloat()) * (max - min))
        return ((raw / 100f).toInt() * 100).coerceIn(min, max)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(widthPx) {
                detectTapGestures { offset ->
                    onWeightChange(weightFromX(offset.x))
                }
            }
            .pointerInput(widthPx) {
                detectDragGestures(
                    onDragStart = { offset -> onWeightChange(weightFromX(offset.x)) },
                    onDrag = { change, _ -> onWeightChange(weightFromX(change.position.x)) }
                )
            }
            .drawBehind {
                val trackHeight = 6.dp.toPx()
                val thumbRadius = 15.dp.toPx()
                val usableStart = thumbRadius
                val usableEnd = size.width - thumbRadius
                val usableWidth = usableEnd - usableStart
                val progress = ((weight - 100).toFloat() / 800f).coerceIn(0f, 1f)
                val thumbX = usableStart + usableWidth * progress
                val trackY = size.height / 2f

                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(usableStart, trackY - trackHeight / 2f),
                    size = Size(usableWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight, trackHeight)
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(usableStart, trackY - trackHeight / 2f),
                    size = Size(thumbX - usableStart, trackHeight),
                    cornerRadius = CornerRadius(trackHeight, trackHeight)
                )

                for (index in 0..8) {
                    val tickX = usableStart + usableWidth * (index / 8f)
                    drawCircle(
                        color = tickColor,
                        radius = 3.2.dp.toPx(),
                        center = Offset(tickX, trackY)
                    )
                }

                drawCircle(
                    color = Color(0x22000000),
                    radius = thumbRadius + 1.dp.toPx(),
                    center = Offset(thumbX, trackY + 1.dp.toPx())
                )
                drawCircle(
                    color = thumbFill,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackY)
                )
                drawCircle(
                    color = activeColor,
                    radius = 5.dp.toPx(),
                    center = Offset(thumbX, trackY)
                )
            }
    )
}
