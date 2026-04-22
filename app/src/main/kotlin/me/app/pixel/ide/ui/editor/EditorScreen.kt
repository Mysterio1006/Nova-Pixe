package me.app.pixel.ide.ui.editor

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.app.pixel.ide.ui.newcanvas.CanvasBg
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    name: String, width: Int, height: Int, bg: String, filePath: String? = null,
    viewModel: EditorViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var toolMenuExpanded by remember { mutableStateOf<Tool?>(null) }

    LaunchedEffect(Unit) {
        if (filePath != null) viewModel.processIntent(EditorIntent.InitProjectFromFile(filePath))
        else viewModel.processIntent(EditorIntent.InitProject(name, width, height, CanvasBg.valueOf(bg)))
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is EditorEffect.NavigateBack -> onNavigateBack()
                is EditorEffect.ShowToolSubMenu -> toolMenuExpanded = effect.tool
                is EditorEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is EditorEffect.SaveProject -> {
                    val projectDir = context.getDir("projects", android.content.Context.MODE_PRIVATE)
                    val projectFile = File(projectDir, "${effect.name}.png")
                    FileOutputStream(projectFile).use { effect.merged1x.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "${effect.name}_${System.currentTimeMillis()}.png")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/NovaPixel")
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { 
                            effect.scaledExport.compress(Bitmap.CompressFormat.PNG, 100, it)
                            Toast.makeText(context, "已保存到项目大厅并导出超清至相册", Toast.LENGTH_LONG).show()
                        }
                    } else Toast.makeText(context, "已保存至项目大厅，相册权限异常", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (state.isFrameSettingsOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(EditorIntent.ToggleFrameSettings) },
            title = { Text("动画播放设置", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("基础帧率 (Base FPS): ${state.playbackFps}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = state.playbackFps.toFloat(),
                            onValueChange = { viewModel.processIntent(EditorIntent.UpdatePlaybackSettings(it.toInt(), state.frameHoldTicks)) },
                            valueRange = 1f..60f,
                            steps = 58
                        )
                    }
                    Column {
                        Text("一拍几 (Holds / 每帧驻留时长): ${state.frameHoldTicks}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = state.frameHoldTicks.toFloat(),
                            onValueChange = { viewModel.processIntent(EditorIntent.UpdatePlaybackSettings(state.playbackFps, it.toInt())) },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        val actualFps = String.format("%.1f", state.playbackFps.toFloat() / state.frameHoldTicks)
                        Text("说明: 实际感官播放速度约为 $actualFps 帧/秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.processIntent(EditorIntent.ToggleFrameSettings) }) { Text("确定") } }
        )
    }

    if (state.isColorPaletteOpen) {
        ColorPaletteDialog(
            initialColor = state.currentColor,
            recentColors = state.recentColors,
            onColorSelected = { viewModel.processIntent(EditorIntent.SetColor(it)) },
            onDismiss = { viewModel.processIntent(EditorIntent.ToggleColorPalette) }
        )
    }

    Scaffold(
        topBar = { EditorTopIsland(state = state, onIntent = viewModel::processIntent) },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = state.isFrameStripOpen && state.currentMode == EditorMode.CANVAS,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    EditorFrameStrip(state = state, onIntent = viewModel::processIntent)
                }
                if (state.currentMode == EditorMode.CANVAS) {
                    EditorBottomDock(state = state, onIntent = viewModel::processIntent, onToolLongClick = { viewModel.triggerToolSubMenu(it) })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.currentMode == EditorMode.CANVAS) EditorCanvasArea(state, viewModel)
            else EditorCodeArea(state, viewModel::processIntent)

            // 图层/独立画布面板
            AnimatedVisibility(
                visible = state.isCanvasPanelOpen && state.currentMode == EditorMode.CANVAS,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                EditorCanvasPanel(state = state, onIntent = viewModel::processIntent)
            }

            // 笔刷设置展开菜单
            if (toolMenuExpanded == Tool.BRUSH || toolMenuExpanded == Tool.ERASER) {
                ModalBottomSheet(onDismissRequest = { toolMenuExpanded = null }) {
                    val isBrush = toolMenuExpanded == Tool.BRUSH
                    val currentSize = if (isBrush) state.brushSize else state.eraserSize
                    val currentShape = if (isBrush) state.brushShape else state.eraserShape
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                        Text("${if (isBrush) "画笔" else "橡皮擦"}高级设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("笔刷粗细 (像素)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Slider(value = currentSize.toFloat(), onValueChange = { viewModel.processIntent(EditorIntent.SetToolSize(toolMenuExpanded!!, it.toInt())) }, valueRange = 1f..32f, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("$currentSize px", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("笔刷触控形态", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(12.dp)).background(if (currentShape == BrushShape.SQUARE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.processIntent(EditorIntent.SetToolShape(toolMenuExpanded!!, BrushShape.SQUARE)) }, contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.onSurface))
                                    Text("硬边缘 (方形)", fontWeight = FontWeight.Bold, color = if (currentShape == BrushShape.SQUARE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Box(modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(12.dp)).background(if (currentShape == BrushShape.CIRCLE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.processIntent(EditorIntent.SetToolShape(toolMenuExpanded!!, BrushShape.CIRCLE)) }, contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                                    Text("柔边缘 (圆形)", fontWeight = FontWeight.Bold, color = if (currentShape == BrushShape.CIRCLE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            } else if (toolMenuExpanded != null) {
                AlertDialog(onDismissRequest = { toolMenuExpanded = null }, title = { Text("${toolMenuExpanded?.name} 工具") }, text = { Text("高级扩展选项规划中...") }, confirmButton = { TextButton(onClick = { toolMenuExpanded = null }) { Text("确定") } })
            }
        }
    }
}