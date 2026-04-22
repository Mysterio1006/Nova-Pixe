package me.app.pixel.ide.ui.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import me.app.pixel.ide.ui.newcanvas.CanvasBg

enum class Tool { HAND, BRUSH, ERASER, FILL, SELECT, PICKER }
enum class EditorMode { CANVAS, CODE }
enum class BrushShape { SQUARE, CIRCLE }
enum class DrawEvent { START, MOVE, END }
enum class SymmetryMode { NONE, HORIZONTAL, VERTICAL, QUAD }

data class PixelFrame(
    val id: Long = System.currentTimeMillis(),
    val bitmap: Bitmap
)

data class PixelCanvas(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val frames: List<PixelFrame>,
    val activeFrameIndex: Int = 0
)

data class EditorState(
    val projectName: String = "",
    val canvasWidth: Int = 64,
    val canvasHeight: Int = 64,
    val background: CanvasBg = CanvasBg.TRANSPARENT,
    
    val showGrid: Boolean = true,
    val currentMode: EditorMode = EditorMode.CANVAS,
    val selectedTool: Tool = Tool.BRUSH,
    
    val selectionRect: android.graphics.Rect? = null,
    val floatingBitmap: Bitmap? = null,
    val floatingOffsetX: Int = 0,
    val floatingOffsetY: Int = 0,
    
    val currentColor: Color = Color.Black,
    val isColorPaletteOpen: Boolean = false,
    val recentColors: List<Color> = emptyList(),
    
    val symmetryMode: SymmetryMode = SymmetryMode.NONE,
    val onionSkinEnabled: Boolean = false,
    
    val brushSize: Int = 1,
    val brushShape: BrushShape = BrushShape.SQUARE,
    val eraserSize: Int = 1,
    val eraserShape: BrushShape = BrushShape.SQUARE,
    
    val isCanvasPanelOpen: Boolean = false,
    val canvases: List<PixelCanvas> = emptyList(),
    val activeCanvasIndex: Int = 0,
    
    val isFrameStripOpen: Boolean = false,
    val isPlaying: Boolean = false,
    val playingFrameIndex: Int = 0,
    
    val isFrameSettingsOpen: Boolean = false,
    val playbackFps: Int = 24,       
    val frameHoldTicks: Int = 3,     
    
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val redrawTrigger: Int = 0,
    
    val codeContent: String = """
        // NovaScript v1.1 核心特性演示
        clear()
        
        // 1. 计算宏: 使用 -> 极简返回值
        macro cx() -> w / 2
        macro cy() -> h / 2
        
        // 2. 将动作存为图章，并利用字符串拼接
        repeat 3 as idx:
            rect(0, 0, w, h, #1A1A24)
            circle(cx(), cy(), 10 + idx * 5, #3399FF)
            save("frame_" + idx) // 动态命名存储
            
        clear()
        
        // 3. 安全隔离的 offset + 钳制函数 clamp
        offset cx() - 10, cy() - 10:
            repeat w as px:
                repeat h as py:
                    // 限定绘制区域只能在 0~20 之间
                    if clamp(px, 0, 20) == px:
                        if clamp(py, 0, 20) == py:
                            dot(px, py, #FFFFCC)
                            
        // 4. 读取刚才拼接字符串存下的图章
        stamp("frame_1", 0, 0)
    """.trimIndent()
)

sealed class EditorIntent {
    data class InitProject(val name: String, val width: Int, val height: Int, val bg: CanvasBg) : EditorIntent()
    data class InitProjectFromFile(val path: String) : EditorIntent()
    
    data class SelectTool(val tool: Tool) : EditorIntent()
    data class SwitchMode(val mode: EditorMode) : EditorIntent()
    object ToggleGrid : EditorIntent()
    
    object ToggleSymmetryMode : EditorIntent()
    object ToggleOnionSkin : EditorIntent()
    
    object ToggleCanvasPanel : EditorIntent()
    object AddCanvas : EditorIntent()
    data class SelectCanvas(val index: Int) : EditorIntent()
    data class RenameCanvas(val index: Int, val newName: String) : EditorIntent()
    data class DuplicateCanvas(val index: Int) : EditorIntent()
    data class DeleteCanvas(val index: Int) : EditorIntent()

    object ToggleFrameStrip : EditorIntent()
    object AddFrame : EditorIntent()
    data class SelectFrame(val index: Int) : EditorIntent()
    object TogglePlay : EditorIntent()
    
    object ToggleFrameSettings : EditorIntent()
    data class UpdatePlaybackSettings(val fps: Int, val holds: Int) : EditorIntent()
    
    object ToggleColorPalette : EditorIntent()
    data class SetColor(val color: Color) : EditorIntent()
    data class PickColorAt(val x: Int, val y: Int) : EditorIntent()
    
    data class SetToolSize(val tool: Tool, val size: Int) : EditorIntent()
    data class SetToolShape(val tool: Tool, val shape: BrushShape) : EditorIntent()
    
    data class DrawAction(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val event: DrawEvent) : EditorIntent()
    data class SelectionAction(val x: Int, val y: Int, val event: DrawEvent) : EditorIntent()
    
    object RequestSaveSingleFrame : EditorIntent()
    object RequestSaveSpriteSheet : EditorIntent()
    
    object Undo : EditorIntent()
    object Redo : EditorIntent()
    object NavigateBack : EditorIntent()
    
    data class UpdateCode(val code: String) : EditorIntent()
    object SyncCodeToCanvas : EditorIntent()
}

sealed class EditorEffect {
    object NavigateBack : EditorEffect()
    data class ShowToolSubMenu(val tool: Tool) : EditorEffect()
    data class SaveProject(val merged1x: Bitmap, val scaledExport: Bitmap, val name: String) : EditorEffect()
    data class ShowToast(val message: String) : EditorEffect()
}