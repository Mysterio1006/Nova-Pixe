package me.app.pixel.ide.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteDialog(
    initialColor: Color,
    recentColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)

    var hexText by remember(currentColor) { 
        mutableStateOf(String.format("#%06X", (0xFFFFFF and currentColor.toArgb())))
    }

    val presetColors = listOf(
        Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFF004D), Color(0xFFFFA300),
        Color(0xFFFFEC27), Color(0xFF00E436), Color(0xFF29ADFF), Color(0xFF83769C),
        Color(0xFFFF77A8), Color(0xFFC2C3C7), Color(0xFF5F574F), Color(0xFFAB5236),
        Color(0xFF008751), Color(0xFF1D2B53), Color(0xFF7E2553), Color(0xFFFF0044)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级调色盘", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 颜色预览块与十六进制输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(0.1f), RoundedCornerShape(12.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { 
                            hexText = it.uppercase()
                            if (it.length == 7 && it.startsWith("#")) {
                                try {
                                    val parsed = Color(android.graphics.Color.parseColor(it))
                                    red = parsed.red
                                    green = parsed.green
                                    blue = parsed.blue
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        label = { Text("HEX") },
                        textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ColorSlider("R", red, Color.Red) { red = it }
                ColorSlider("G", green, Color.Green) { green = it }
                ColorSlider("B", blue, Color.Blue) { blue = it }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (recentColors.isNotEmpty()) {
                    Text("最近使用", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 50.dp)
                    ) {
                        items(recentColors.take(8)) { color ->
                            ColorSwatch(color) {
                                red = color.red; green = color.green; blue = color.blue
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("经典预设 (Pico-8)", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 120.dp)
                ) {
                    items(presetColors) { color ->
                        ColorSwatch(color) {
                            red = color.red; green = color.green; blue = color.blue
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor); onDismiss() }) { Text("应用颜色") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ColorSwatch(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f), CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, tint: Color, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.width(24.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint.copy(alpha = 0.5f))
        )
    }
}