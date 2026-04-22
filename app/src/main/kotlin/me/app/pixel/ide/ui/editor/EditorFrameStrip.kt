package me.app.pixel.ide.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun EditorFrameStrip(state: EditorState, onIntent: (EditorIntent) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        val currentCanvas = state.canvases.getOrNull(state.activeCanvasIndex) ?: return@Surface
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // 洋葱皮模式开关
                val onionSkinTint by animateColorAsState(targetValue = if (state.onionSkinEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { onIntent(EditorIntent.ToggleOnionSkin) }) {
                    Icon(Icons.Rounded.FilterNone, contentDescription = "洋葱皮模式", tint = onionSkinTint)
                }

                IconButton(onClick = { onIntent(EditorIntent.ToggleFrameSettings) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "播放设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                val playTint by animateColorAsState(targetValue = if (state.isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                IconButton(onClick = { onIntent(EditorIntent.TogglePlay) }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = "播放/停止", tint = playTint)
                }
            }
            
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 读取触发器以保证帧的渲染强制刷新
                val trigger = state.redrawTrigger
                itemsIndexed(currentCanvas.frames) { index, frame ->
                    val isSelected = index == currentCanvas.activeFrameIndex
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(if (isSelected && !state.isPlaying) 3.dp else 0.dp, if (isSelected && !state.isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .clickable(enabled = !state.isPlaying) { onIntent(EditorIntent.SelectFrame(index)) }
                    ) {
                        Image(
                            bitmap = frame.bitmap.asImageBitmap(),
                            contentDescription = "Frame $index",
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                            filterQuality = FilterQuality.None
                        )
                        // 帧数角标
                        Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(0.6f), RoundedCornerShape(topStart = 8.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = !state.isPlaying) { onIntent(EditorIntent.AddFrame) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "新增动画帧", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}