package me.app.pixel.ide.ui.project

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagementScreen(
    onNavigateBack: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<File>>(emptyList()) }
    var showImportMenu by remember { mutableStateOf(false) }

    fun loadProjects() {
        val dir = context.getDir("projects", Context.MODE_PRIVATE)
        projects = dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) { loadProjects() }

    // 1. 导出 ZIP 启动器
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                exportAllProjectsToZip(context, it, projects)
                Toast.makeText(context, "所有项目已成功导出！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. 导入单张 PNG 启动器
    val importPngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                importProjectFromPng(context, it)
                loadProjects()
                Toast.makeText(context, "图片导入成功！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. 导入 ZIP 启动器
    val importZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                val count = importProjectsFromZip(context, it)
                loadProjects()
                Toast.makeText(context, "成功导入 $count 个项目！", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("项目大厅", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    // 导入操作组
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Rounded.Download, contentDescription = "导入", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导入单张图片 (.png)") },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importPngLauncher.launch(arrayOf("image/png"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入项目包 (.zip)") },
                                leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                                }
                            )
                        }
                    }
                    
                    // 导出操作
                    IconButton(
                        onClick = {
                            if (projects.isEmpty()) {
                                Toast.makeText(context, "没有可导出的项目", Toast.LENGTH_SHORT).show()
                            } else {
                                // 启动系统文件管理器并默认命名文件
                                exportZipLauncher.launch("NovaPixel_Projects_Export.zip")
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = "全部导出", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("暂无项目，快去新建一个画布或导入吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(projects) { file ->
                    ProjectCard(
                        file = file,
                        onClick = { onOpenProject(file.absolutePath) },
                        onDelete = { file.delete(); loadProjects() }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectCard(file: File, onClick: () -> Unit, onDelete: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            bitmap = bmp?.asImageBitmap()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除？") },
            text = { Text("将永久删除项目「${file.nameWithoutExtension}」，该操作无法恢复。") },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 预览图区域
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = file.nameWithoutExtension,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        filterQuality = FilterQuality.None
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = file.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                    Text(text = date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ==========================================
// 核心后台 IO 引擎：文件流拷贝与压缩解压
// ==========================================

suspend fun importProjectFromPng(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
    try {
        val dir = context.getDir("projects", Context.MODE_PRIVATE)
        var fileName = "Imported_${System.currentTimeMillis()}.png"
        
        // 尝试从系统的 Uri 中读取原始文件名
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                val originalName = cursor.getString(nameIndex)
                if (originalName.endsWith(".png", true)) fileName = originalName
            }
        }

        val outFile = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun importProjectsFromZip(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    var importedCount = 0
    try {
        val dir = context.getDir("projects", Context.MODE_PRIVATE)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".png", ignoreCase = true)) {
                        // File(entry.name).name 用于剥离压缩包中可能带有的文件夹层级，只取最终文件名
                        val outFile = File(dir, File(entry.name).name)
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                            importedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext importedCount
}

suspend fun exportAllProjectsToZip(context: Context, uri: Uri, projects: List<File>) = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zos ->
                for (file in projects) {
                    FileInputStream(file).use { fis ->
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}