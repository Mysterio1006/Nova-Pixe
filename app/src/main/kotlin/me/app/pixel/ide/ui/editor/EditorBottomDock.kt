
package me.app.pixel.ide.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun EditorBottomDock(
    state: EditorState, 
    onIntent: (EditorIntent) -> Unit,
    onToolLongClick: (Tool) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton(Icons.Rounded.Brush, state.selectedTool == Tool.BRUSH, { onIntent(EditorIntent.SelectTool(Tool.BRUSH)) }, { onToolLongClick(Tool.BRUSH) })
                    ToolButton(Icons.Rounded.AutoFixNormal, state.selectedTool == Tool.ERASER, { onIntent(EditorIntent.SelectTool(Tool.ERASER)) }, { onToolLongClick(Tool.ERASER) })
                    ToolButton(Icons.Rounded.FormatColorFill, state.selectedTool == Tool.FILL, { onIntent(EditorIntent.SelectTool(Tool.FILL)) }, { onToolLongClick(Tool.FILL) })
                    ToolButton(Icons.Rounded.Colorize, state.selectedTool == Tool.PICKER, { onIntent(EditorIntent.SelectTool(Tool.PICKER)) }, { onToolLongClick(Tool.PICKER) })
                    
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(state.currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { onIntent(EditorIntent.ToggleColorPalette) }
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton(Icons.Rounded.PanTool, state.selectedTool == Tool.HAND, { onIntent(EditorIntent.SelectTool(Tool.HAND)) }, { onToolLongClick(Tool.HAND) })
                    ToolButton(Icons.Rounded.HighlightAlt, state.selectedTool == Tool.SELECT, { onIntent(EditorIntent.SelectTool(Tool.SELECT)) }, { onToolLongClick(Tool.SELECT) })
                    ToolButton(if (state.showGrid) Icons.Rounded.GridOn else Icons.Rounded.GridOff, state.showGrid, { onIntent(EditorIntent.ToggleGrid) }, { })
                    
                    // 独立画布管理器
                    ToolButton(Icons.Rounded.PhotoLibrary, state.isCanvasPanelOpen, { onIntent(EditorIntent.ToggleCanvasPanel) }, { })
                    // 动画帧管理器
                    ToolButton(Icons.Rounded.Animation, state.isFrameStripOpen, { onIntent(EditorIntent.ToggleFrameStrip) }, { })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) { Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
}