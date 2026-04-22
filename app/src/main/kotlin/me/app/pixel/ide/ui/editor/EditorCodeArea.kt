package me.app.pixel.ide.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.app.pixel.ide.ui.components.MarkdownRenderer

@Composable
fun EditorCodeArea(state: EditorState, onIntent: (EditorIntent) -> Unit) {
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        DslHelpDialog(onDismiss = { showHelpDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = " DSL 绘制渲染", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.HelpOutline, 
                        contentDescription = "语法帮助", 
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Button(
                onClick = { onIntent(EditorIntent.SyncCodeToCanvas) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "运行")
                Spacer(Modifier.width(8.dp))
                Text("渲染画布")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = state.codeContent,
            onValueChange = { onIntent(EditorIntent.UpdateCode(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun DslHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var markdownContent by remember { mutableStateOf("加载中...") }

    // 从 assets 中异步加载文档内容
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                markdownContent = context.assets.open("NovaScript_Reference.md")
                    .bufferedReader()
                    .use { it.readText() }
            } catch (e: Exception) {
                markdownContent = "无法加载官方参考文档: ${e.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("帮助中心", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                SelectionContainer {
                    MarkdownRenderer(markdown = markdownContent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我已了解") }
        }
    )
}