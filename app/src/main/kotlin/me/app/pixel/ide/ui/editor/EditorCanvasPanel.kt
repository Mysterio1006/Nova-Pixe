
package me.app.pixel.ide.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EditorCanvasPanel(state: EditorState, onIntent: (EditorIntent) -> Unit, modifier: Modifier = Modifier) {
    var renameDialogIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (renameDialogIndex != null) {
        AlertDialog(
            onDismissRequest = { renameDialogIndex = null },
            title = { Text("重命名画布") },
            text = { 
                OutlinedTextField(
                    value = renameText, 
                    onValueChange = { renameText = it },
                    singleLine = true
                ) 
            },
            confirmButton = {
                Button(onClick = { 
                    onIntent(EditorIntent.RenameCanvas(renameDialogIndex!!, renameText))
                    renameDialogIndex = null 
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogIndex = null }) { Text("取消") }
            }
        )
    }

    Surface(
        modifier = modifier.width(260.dp).height(380.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("独立画布集 (Sprites)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.canvases) { index, canvas ->
                    var expanded by remember { mutableStateOf(false) }
                    val isSelected = state.activeCanvasIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { onIntent(EditorIntent.SelectCanvas(index)) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = canvas.name, 
                            modifier = Modifier.weight(1f), 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        
                        Box {
                            IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多", modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = { expanded = false; renameText = canvas.name; renameDialogIndex = index })
                                DropdownMenuItem(text = { Text("复制") }, onClick = { expanded = false; onIntent(EditorIntent.DuplicateCanvas(index)) })
                                if (state.canvases.size > 1) {
                                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onIntent(EditorIntent.DeleteCanvas(index)) })
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onIntent(EditorIntent.AddCanvas) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "新建画布")
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建独立画布")
            }
        }
    }
}