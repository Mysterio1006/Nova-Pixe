package me.app.pixel.ide.ui.newcanvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewCanvasScreen(
    viewModel: NewCanvasViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (name: String, width: Int, height: Int, bg: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is NewCanvasEffect.NavigateBack -> onNavigateBack()
                is NewCanvasEffect.NavigateToEditor -> {
                    onNavigateToEditor(effect.name, effect.width, effect.height, effect.bg.name)
                }
                is NewCanvasEffect.ShowError -> {
                    // TODO: 可以在这里添加 Toast 或 Snackbar
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建画布", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.processIntent(NewCanvasIntent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // 底部创建一个大大的创建按钮 (MD3 Expressive 风格)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = { viewModel.processIntent(NewCanvasIntent.CreateCanvas) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("创建画布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. 项目名称
            SectionColumn("项目名称") {
                OutlinedTextField(
                    value = state.projectName,
                    onValueChange = { viewModel.processIntent(NewCanvasIntent.UpdateProjectName(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // 2. 宽高与锁定
            SectionColumn("画布尺寸 (px)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.width,
                        onValueChange = { viewModel.processIntent(NewCanvasIntent.UpdateWidth(it)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("宽") },
                        singleLine = true
                    )
                    
                    IconButton(
                        onClick = { viewModel.processIntent(NewCanvasIntent.ToggleRatioLock) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isRatioLocked) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                            contentDescription = "px 锁定",
                            tint = if (state.isRatioLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = state.height,
                        onValueChange = { viewModel.processIntent(NewCanvasIntent.UpdateHeight(it)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("高") },
                        singleLine = true
                    )
                }
            }

            // 3. 尺寸预设
            SectionColumn("常用预设") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096)
                    presets.forEach { size ->
                        FilterChip(
                            selected = (state.width == size.toString() && state.height == size.toString()),
                            onClick = { viewModel.processIntent(NewCanvasIntent.ApplyPreset(size)) },
                            label = { Text("${size}×${size}") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // 4. 背景色选择
            SectionColumn("背景颜色") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BgColorSelector(
                        title = "透明",
                        isSelected = state.background == CanvasBg.TRANSPARENT,
                        // 用淡灰色背景模拟透明
                        color = MaterialTheme.colorScheme.surfaceVariant, 
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.processIntent(NewCanvasIntent.SelectBackground(CanvasBg.TRANSPARENT)) }
                    )
                    BgColorSelector(
                        title = "白",
                        isSelected = state.background == CanvasBg.WHITE,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.processIntent(NewCanvasIntent.SelectBackground(CanvasBg.WHITE)) }
                    )
                    BgColorSelector(
                        title = "黑",
                        isSelected = state.background == CanvasBg.BLACK,
                        color = Color.Black,
                        textColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.processIntent(NewCanvasIntent.SelectBackground(CanvasBg.BLACK)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionColumn(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        content()
    }
}

@Composable
private fun BgColorSelector(
    title: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable { onClick() }
            .then(
                // 对白色背景加一个极其微弱的边框防止和底色融为一体
                if (color == Color.White && !isSelected) Modifier.background(color, RoundedCornerShape(12.dp)) 
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            border = BorderStroke(borderWidth, borderColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            // 对纯白色加个极细的内发丝线
            if (color == Color.White && !isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(1.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}