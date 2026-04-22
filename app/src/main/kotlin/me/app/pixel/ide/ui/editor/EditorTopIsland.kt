package me.app.pixel.ide.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopIsland(state: EditorState, onIntent: (EditorIntent) -> Unit) {
    // 增加局部状态控制保存菜单的展开
    var showSaveMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable {
                    val nextMode = if (state.currentMode == EditorMode.CANVAS) EditorMode.CODE else EditorMode.CANVAS
                    onIntent(EditorIntent.SwitchMode(nextMode))
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (state.currentMode == EditorMode.CANVAS) Icons.Rounded.Brush else Icons.Rounded.Code,
                        contentDescription = "模式切换",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (state.currentMode == EditorMode.CANVAS) "画布模式" else "代码模式",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { onIntent(EditorIntent.NavigateBack) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            if (state.currentMode == EditorMode.CANVAS) {
                val symmetryTint by animateColorAsState(
                    targetValue = if (state.symmetryMode != SymmetryMode.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { onIntent(EditorIntent.ToggleSymmetryMode) }) {
                    Icon(Icons.Rounded.Flip, contentDescription = "对称模式", tint = symmetryTint)
                }
            }

            IconButton(onClick = { onIntent(EditorIntent.Undo) }, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = { onIntent(EditorIntent.Redo) }, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "重做")
            }
            
            // === 全新改版的保存菜单按钮 ===
            Box {
                IconButton(onClick = { showSaveMenu = true }) {
                    Icon(Icons.Rounded.Save, contentDescription = "保存选项", tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(
                    expanded = showSaveMenu,
                    onDismissRequest = { showSaveMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("保存当前单帧 (PNG)") },
                        onClick = {
                            showSaveMenu = false
                            onIntent(EditorIntent.RequestSaveSingleFrame)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出全序列精灵图 (Sprite Sheet)", color = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showSaveMenu = false
                            onIntent(EditorIntent.RequestSaveSpriteSheet)
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}