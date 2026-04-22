package me.app.pixel.ide.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToEditor: () -> Unit,
    onNavigateToFilePicker: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAiTerminal: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToEditor -> onNavigateToEditor()
                is HomeEffect.NavigateToFilePicker -> onNavigateToFilePicker()
                is HomeEffect.NavigateToSettings -> onNavigateToSettings()
                is HomeEffect.NavigateToAiTerminal -> onNavigateToAiTerminal()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Nova Pixel",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nova Pixel",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "全新一代高级像素画编辑器",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
            )

            if (state.isFirstTime) {
                FirstTimeActionLayout(
                    onAction = { intent -> viewModel.processIntent(intent) }
                )
            }
        }
    }
}

@Composable
private fun FirstTimeActionLayout(onAction: (HomeIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExpressiveActionCard(
            title = "AI 新建",
            icon = Icons.Rounded.ChatBubbleOutline,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 8.dp),
            modifier = Modifier.fillMaxWidth().height(140.dp),
            onClick = { onAction(HomeIntent.OpenAiTerminal) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveActionCard(
                title = "新建画布",
                icon = Icons.Rounded.Add,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 32.dp, bottomEnd = 8.dp, bottomStart = 32.dp),
                modifier = Modifier.weight(1f).height(160.dp),
                onClick = { onAction(HomeIntent.CreateNewProject) }
            )

            // 将打开本地改为项目管理
            ExpressiveActionCard(
                title = "项目管理",
                icon = Icons.Rounded.FolderOpen,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 8.dp, bottomEnd = 32.dp, bottomStart = 8.dp),
                modifier = Modifier.weight(1f).height(160.dp),
                onClick = { onAction(HomeIntent.OpenLocalProject) }
            )
        }

        ExpressiveActionCard(
            title = "偏好设置",
            icon = Icons.Rounded.Settings,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(100.dp),
            onClick = { onAction(HomeIntent.OpenSettings) }
        )
    }
}

@Composable
fun ExpressiveActionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}