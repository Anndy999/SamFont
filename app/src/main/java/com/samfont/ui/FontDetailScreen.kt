package com.samfont.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samfont.BuildConfig
import com.samfont.core.font.FontFamilyModel
import com.samfont.core.font.FontState
import com.samfont.core.font.variation.FontVariationAxis
import com.samfont.core.preview.FontPreviewEngine
import java.io.File

@Composable
fun FontDetailScreen(
    modifier: Modifier = Modifier,
    font: FontFamilyModel,
    canApplySystemFont: Boolean,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    var selectedWeight by rememberSaveable(font.id) {
        mutableIntStateOf(font.files.firstOrNull()?.weight ?: 400)
    }
    val visibleAxes = remember(font.variationInfo) {
        font.variationInfo?.axes?.filter { !it.hidden || BuildConfig.DEBUG }.orEmpty()
    }
    var axisValues by remember(font.id) {
        mutableStateOf(visibleAxes.associate { it.tag to it.defaultValue })
    }
    val previewFile = font.files.firstOrNull()
    val variationSettings = if (axisValues.isEmpty()) {
        mapOf("wght" to selectedWeight.toFloat())
    } else {
        axisValues
    }
    val typeface = remember(previewFile?.path, selectedWeight, variationSettings) {
        previewFile?.let {
            FontPreviewEngine.loadTypeface(
                fontFile = it,
                weight = selectedWeight,
                variationSettings = variationSettings
            )
        }
    }
    val previewFamily = remember(typeface) { typeface?.let { FontFamily(it) } ?: FontFamily.Default }
    val fileExists = previewFile?.path?.let { File(it).exists() } == true
    val buttonEnabled = when (font.state) {
        FontState.Available,
        FontState.Imported -> true
        FontState.Installed -> canApplySystemFont
        FontState.Applied,
        FontState.Broken -> false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(text = "Back") }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = font.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${font.state.name} / ${if (font.isVariableFont) "Variable" else "Static"} / ${font.fileType.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Variable Font", style = MaterialTheme.typography.headlineSmall)
                Text(text = "wght Weight  $selectedWeight", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = selectedWeight.toFloat(),
                    onValueChange = {
                        selectedWeight = it.toInt().coerceIn(100, 900)
                        if (axisValues.containsKey("wght")) {
                            axisValues = axisValues + ("wght" to selectedWeight.toFloat())
                        }
                    },
                    valueRange = 100f..900f,
                    steps = 7
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "100", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "900", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (visibleAxes.isNotEmpty()) {
                    AxisControls(
                        axes = visibleAxes,
                        axisValues = axisValues,
                        onAxisValueChange = { tag, value ->
                            axisValues = axisValues + (tag to value)
                            if (tag == "wght") selectedWeight = value.toInt().coerceIn(100, 900)
                        },
                        onReset = {
                            axisValues = visibleAxes.associate { it.tag to it.defaultValue }
                            axisValues["wght"]?.let { selectedWeight = it.toInt().coerceIn(100, 900) }
                        }
                    )
                } else {
                    OutlinedButton(onClick = { selectedWeight = 400 }) {
                        Text(text = "Restore default")
                    }
                }

                val instances = font.variationInfo?.namedInstances.orEmpty()
                if (instances.isNotEmpty()) {
                    Text(text = "Named Instances", style = MaterialTheme.typography.titleMedium)
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Preview", style = MaterialTheme.typography.titleLarge)
                if (!fileExists) {
                    Text(text = "Font file missing. Preview falls back to system font.", color = Color(0xFFB3261E))
                } else if (typeface == null) {
                    Text(
                        text = "Android preview engine cannot load this font. The file remains in the library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                listOf(
                    "山高月小，水落石出",
                    "The quick brown fox jumps over the lazy dog",
                    "1234567890",
                    "符号：，。！？@#￥%&*"
                ).forEach { text ->
                    Text(
                        text = text,
                        style = TextStyle(
                            fontFamily = previewFamily,
                            fontSize = 20.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight(selectedWeight)
                        )
                    )
                }
            }
        }

        font.compatibilityReport?.let { report ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Compatibility", style = MaterialTheme.typography.titleLarge)
                    Text(text = "Type: ${report.fileType.uppercase()} / TTC: ${yesNo(report.isTtc)}")
                    Text(text = "Variable: ${yesNo(report.isVariableFont)} / Axes: ${report.axes.size}")
                    Text(text = "Android preview: ${yesNo(report.androidPreviewAvailable)}")
                    Text(text = "CJK: ${yesNo(report.hasCjkCoverage)} / Simplified: ${yesNo(report.hasSimplifiedChineseCoverage)} / Traditional: ${yesNo(report.hasTraditionalChineseCoverage)}")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = buttonEnabled,
            onClick = onPrimaryAction
        ) {
            Text(text = primaryButtonText(font.state))
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

    if (standardAxes.isNotEmpty()) {
        Text(text = "Standard axes", style = MaterialTheme.typography.titleMedium)
        standardAxes.forEach { axis ->
            AxisSlider(axis, axisValues[axis.tag] ?: axis.defaultValue, onAxisValueChange)
        }
    }
    if (customAxes.isNotEmpty()) {
        Text(text = "Advanced axes", style = MaterialTheme.typography.titleMedium)
        customAxes.forEach { axis ->
            AxisSlider(axis, axisValues[axis.tag] ?: axis.defaultValue, onAxisValueChange)
        }
    }
    OutlinedButton(onClick = onReset) { Text(text = "Restore default") }
}

@Composable
private fun AxisSlider(
    axis: FontVariationAxis,
    value: Float,
    onAxisValueChange: (String, Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${axis.tag} ${axis.name ?: ""}")
            Text(text = trimAxisValue(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(axis.minValue, axis.maxValue),
            onValueChange = { onAxisValueChange(axis.tag, it.coerceIn(axis.minValue, axis.maxValue)) },
            valueRange = axis.minValue..axis.maxValue
        )
    }
}

private fun primaryButtonText(state: FontState): String = when (state) {
    FontState.Available,
    FontState.Imported -> "安装"
    FontState.Installed -> "应用"
    FontState.Applied -> "当前已应用"
    FontState.Broken -> "字体不可用"
}

private fun trimAxisValue(value: Float): String {
    val intValue = value.toInt()
    return if (value == intValue.toFloat()) intValue.toString() else "%.2f".format(value)
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
