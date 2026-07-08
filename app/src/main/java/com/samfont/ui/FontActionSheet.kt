package com.samfont.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import com.samfont.theme.SamFontColors
import com.samfont.theme.SamFontDimens
import java.io.File

@Composable
fun FontActionSheet(
    font: FontFamilyModel,
    canApplySystemFont: Boolean,
    onCancel: () -> Unit,
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
    val typeface = remember(previewFile?.path, selectedWeight, axisValues) {
        previewFile?.let {
            FontPreviewEngine.loadTypeface(
                fontFile = it,
                weight = selectedWeight,
                variationSettings = axisValues.ifEmpty { mapOf("wght" to selectedWeight.toFloat()) }
            )
        }
    }
    val previewFamily = remember(typeface) { typeface?.let { FontFamily(it) } ?: FontFamily.Default }
    val fileExists = previewFile?.path?.let { File(it).exists() } == true
    val buttonEnabled = when (font.state) {
        FontState.Imported,
        FontState.Generated,
        FontState.Failed -> true
        FontState.SystemInstalled -> canApplySystemFont
        FontState.Generating,
        FontState.Installing,
        FontState.Applying,
        FontState.Applied,
        FontState.Broken -> false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Variable Font", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = font.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = SamFontColors.TextSecondary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                text = "The quick brown fox",
                style = TextStyle(
                    fontFamily = previewFamily,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight(selectedWeight)
                )
            )
        }

        Text(text = "wght  Weight", style = MaterialTheme.typography.titleMedium)
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
            Text(text = "100", color = SamFontColors.TextSecondary)
            Text(text = "900", color = SamFontColors.TextSecondary)
        }
        Text(
            text = "Bold auto-generated at: $selectedWeight · ${weightLabel(selectedWeight)}",
            style = MaterialTheme.typography.titleMedium
        )

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
        }

        val instances = font.variationInfo?.namedInstances.orEmpty()
        if (instances.isNotEmpty()) {
            Text(text = "Named Instances", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                instances.forEach { instance ->
                    OutlinedButton(onClick = {
                        axisValues = axisValues + instance.coordinates
                        instance.coordinates["wght"]?.let {
                            selectedWeight = it.toInt().coerceIn(100, 900)
                        }
                    }) {
                        Text(text = instance.name ?: "Instance ${instance.nameId}")
                    }
                }
            }
        }

        if (!fileExists || typeface == null) {
            Text(
                text = if (!fileExists) {
                    "Font file missing. Preview falls back to system font."
                } else {
                    "Android preview engine cannot load this font. The file remains in SamFont."
                },
                color = SamFontColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        HorizontalDivider(color = SamFontColors.Divider)

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel
        ) {
            Text(text = "Cancel")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = buttonEnabled,
            onClick = onPrimaryAction,
            shape = RoundedCornerShape(SamFontDimens.PillRadius)
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
    OutlinedButton(onClick = onReset) {
        Text(text = "Restore default")
    }
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
            Text(text = trimAxisValue(value), color = SamFontColors.TextSecondary)
        }
        Slider(
            value = value.coerceIn(axis.minValue, axis.maxValue),
            onValueChange = { onAxisValueChange(axis.tag, it.coerceIn(axis.minValue, axis.maxValue)) },
            valueRange = axis.minValue..axis.maxValue
        )
    }
}

private fun primaryButtonText(state: FontState): String = when (state) {
    FontState.Imported,
    FontState.Failed -> "Generate Fonts"
    FontState.Generated -> "Install"
    FontState.SystemInstalled -> "Apply"
    FontState.Applied -> "Current applied"
    FontState.Generating -> "Generating"
    FontState.Installing -> "Installing"
    FontState.Applying -> "Applying"
    FontState.Broken -> "Font unavailable"
}

private fun trimAxisValue(value: Float): String {
    val intValue = value.toInt()
    return if (value == intValue.toFloat()) intValue.toString() else "%.2f".format(value)
}

private fun weightLabel(weight: Int): String = when (weight) {
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
