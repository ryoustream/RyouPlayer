package com.ryoustream.player.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryoustream.player.domain.model.SubtitleStyle

/**
 * Full subtitle style customization bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleSheet(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SubtitleStyleContent(
            style         = currentStyle,
            onStyleChange = onStyleChange,
            onDismiss     = onDismiss,
        )
    }
}

@Composable
private fun SubtitleStyleContent(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var s by remember(style) { mutableStateOf(style) }

    LazyColumn(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding      = PaddingValues(bottom = 32.dp),
    ) {
        // Header
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Subtitle Style", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = {
                        s = SubtitleStyle()
                        onStyleChange(s)
                    }) { Text("Reset") }
                    TextButton(onClick = { onStyleChange(s); onDismiss() }) { Text("Apply") }
                }
            }
        }

        // Preview
        item {
            SubtitlePreview(style = s)
        }

        // ── Font Size ──────────────────────────────────────────────────────
        item {
            SectionLabel("Font Size: ${s.fontSize.value.toInt()}sp")
            Slider(
                value         = s.fontSize.value,
                onValueChange = { s = s.copy(fontSize = it.sp) },
                valueRange    = 10f..48f,
                steps         = 37,
            )
        }

        // ── Font Weight ────────────────────────────────────────────────────
        item {
            SectionLabel("Font Weight")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Normal" to FontWeight.Normal,
                    "Medium" to FontWeight.Medium,
                    "Bold"   to FontWeight.Bold,
                ).forEach { (label, weight) ->
                    FilterChip(
                        selected = s.fontWeight == weight,
                        onClick  = { s = s.copy(fontWeight = weight) },
                        label    = { Text(label) },
                    )
                }
            }
        }

        // ── Text Color ─────────────────────────────────────────────────────
        item {
            SectionLabel("Text Color")
            ColorPicker(
                selected  = s.textColor,
                onChange  = { s = s.copy(textColor = it) },
                colors    = textColorPresets,
            )
        }

        // ── Background ─────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                SectionLabel("Background")
                Switch(
                    checked         = s.backgroundEnabled,
                    onCheckedChange = { s = s.copy(backgroundEnabled = it) },
                )
            }
            if (s.backgroundEnabled) {
                ColorPicker(
                    selected = s.backgroundColor,
                    onChange = { s = s.copy(backgroundColor = it) },
                    colors   = backgroundColorPresets,
                    showAlpha = true,
                )
            }
        }

        // ── Outline ────────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                SectionLabel("Text Outline")
                Switch(
                    checked         = s.outlineEnabled,
                    onCheckedChange = { s = s.copy(outlineEnabled = it) },
                )
            }
            if (s.outlineEnabled) {
                SectionLabel("Outline Size: ${"%.1f".format(s.outlineWidth)}")
                Slider(
                    value         = s.outlineWidth,
                    onValueChange = { s = s.copy(outlineWidth = it) },
                    valueRange    = 0f..8f,
                )
                ColorPicker(
                    selected = s.outlineColor,
                    onChange = { s = s.copy(outlineColor = it) },
                    colors   = outlineColorPresets,
                )
            }
        }

        // ── Shadow ─────────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                SectionLabel("Shadow")
                Switch(
                    checked         = s.shadowEnabled,
                    onCheckedChange = { s = s.copy(shadowEnabled = it) },
                )
            }
        }

        // ── Vertical Position ──────────────────────────────────────────────
        item {
            SectionLabel("Vertical Position: ${(s.verticalPosition * 100).toInt()}%")
            Slider(
                value         = s.verticalPosition,
                onValueChange = { s = s.copy(verticalPosition = it) },
                valueRange    = 0.5f..0.99f,
            )
        }
    }
}

@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center,
    ) {
        // Simple preview using styled Text
        val bgColor = if (style.backgroundEnabled) style.backgroundColor else Color.Transparent
        Box(
            modifier         = Modifier
                .background(bgColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "Sample Subtitle Text 字幕预览",
                color      = style.textColor,
                fontSize   = (style.fontSize.value.coerceIn(12f, 22f)).sp,
                fontWeight = style.fontWeight,
                textAlign  = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ColorPicker(
    selected: Color,
    onChange: (Color) -> Unit,
    colors: List<Pair<String, Color>>,
    showAlpha: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxWidth(),
        ) {
            colors.forEach { (label, color) ->
                ColorSwatch(
                    color    = color,
                    selected = colorApproxEquals(selected, color),
                    label    = label,
                    onClick  = { onChange(color) },
                )
            }
        }
        if (showAlpha) {
            // Alpha slider for background
            val alpha = selected.alpha
            Text(
                "Opacity: ${(alpha * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value         = alpha,
                onValueChange = { onChange(selected.copy(alpha = it)) },
                valueRange    = 0f..1f,
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color, selected: Boolean, label: String, onClick: () -> Unit,
) {
    Box(
        modifier         = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check, null,
                tint     = if (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f > 0.5f)
                    Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun colorApproxEquals(a: Color, b: Color): Boolean {
    return kotlin.math.abs(a.red - b.red) < 0.05f &&
           kotlin.math.abs(a.green - b.green) < 0.05f &&
           kotlin.math.abs(a.blue - b.blue) < 0.05f
}

// ─── Color Presets ────────────────────────────────────────────────────────────

private val textColorPresets = listOf(
    "White"  to Color.White,
    "Yellow" to Color.Yellow,
    "Cyan"   to Color.Cyan,
    "Green"  to Color(0xFF00FF80),
    "Orange" to Color(0xFFFF8C00),
    "Red"    to Color(0xFFFF3333),
    "Black"  to Color.Black,
)

private val backgroundColorPresets = listOf(
    "Black"  to Color.Black.copy(alpha = 0.6f),
    "Dark"   to Color(0xFF1A1A1A).copy(alpha = 0.7f),
    "Navy"   to Color(0xFF001040).copy(alpha = 0.7f),
    "None"   to Color.Transparent,
)

private val outlineColorPresets = listOf(
    "Black"  to Color.Black,
    "Dark"   to Color(0xFF222222),
    "Navy"   to Color(0xFF000033),
    "White"  to Color.White,
    "Red"    to Color(0xFF660000),
)
