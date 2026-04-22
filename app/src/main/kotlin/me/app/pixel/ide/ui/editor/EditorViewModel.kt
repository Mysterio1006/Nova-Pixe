package me.app.pixel.ide.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.app.pixel.ide.ui.newcanvas.CanvasBg
import java.io.File
import kotlin.math.max
import kotlin.math.min

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorState())
    val uiState: StateFlow<EditorState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<EditorEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private val historyManager = HistoryManager()
    private val drawingEngine = DrawingEngine()
    private var playJob: Job? = null

    // 选区交互状态
    private var selectionStartX = 0
    private var selectionStartY = 0
    private var moveStartX = 0
    private var moveStartY = 0
    private var initialFloatingOffsetX = 0
    private var initialFloatingOffsetY = 0
    private var isSelecting = false
    private var isMovingSelection = false

    fun processIntent(intent: EditorIntent) {
        val state = _uiState.value
        
        when (intent) {
            is EditorIntent.InitProject -> {
                if (state.canvases.isEmpty()) {
                    val initialBmp = drawingEngine.createClearBitmap(intent.width, intent.height, intent.bg)
                    val initCanvas = PixelCanvas(name = intent.name.ifBlank { "主画布" }, frames = listOf(PixelFrame(bitmap = initialBmp)))
                    _uiState.update { 
                        it.copy(
                            projectName = intent.name, canvasWidth = intent.width, canvasHeight = intent.height,
                            background = intent.bg, canvases = listOf(initCanvas), activeCanvasIndex = 0
                        ) 
                    }
                    updateUndoRedoState()
                }
            }
            is EditorIntent.InitProjectFromFile -> {
                if (state.canvases.isEmpty()) {
                    val file = File(intent.path)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        val mutableBmp = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        val initCanvas = PixelCanvas(name = file.nameWithoutExtension, frames = listOf(PixelFrame(bitmap = mutableBmp)))
                        _uiState.update {
                            it.copy(
                                projectName = file.nameWithoutExtension, canvasWidth = bitmap.width, canvasHeight = bitmap.height,
                                background = CanvasBg.TRANSPARENT, canvases = listOf(initCanvas), activeCanvasIndex = 0
                            )
                        }
                        updateUndoRedoState()
                    }
                }
            }
            is EditorIntent.SelectionAction -> handleSelectionAction(intent, state)
            is EditorIntent.DrawAction -> {
                if (state.isPlaying) return
                if (state.selectedTool == Tool.PICKER) {
                    if (intent.event == DrawEvent.START || intent.event == DrawEvent.MOVE) pickColor(intent.x1, intent.y1)
                    return
                }

                val activeCanvasObj = state.canvases.getOrNull(state.activeCanvasIndex) ?: return
                val activeFrame = activeCanvasObj.frames.getOrNull(activeCanvasObj.activeFrameIndex) ?: return
                val bmp = activeFrame.bitmap

                val drawColor = if (state.selectedTool == Tool.ERASER) android.graphics.Color.TRANSPARENT else android.graphics.Color.argb(
                    (state.currentColor.alpha * 255).toInt(), (state.currentColor.red * 255).toInt(),
                    (state.currentColor.green * 255).toInt(), (state.currentColor.blue * 255).toInt()
                )
                
                val size = if (state.selectedTool == Tool.ERASER) state.eraserSize else state.brushSize
                val shape = if (state.selectedTool == Tool.ERASER) state.eraserShape else state.brushShape

                when (intent.event) {
                    DrawEvent.START -> {
                        historyManager.onDrawStart(bmp)
                        drawingEngine.resetBounds()
                        if (state.selectedTool == Tool.FILL) drawingEngine.performFloodFill(bmp, intent.x0, intent.y0, drawColor)
                        else drawingEngine.drawStroke(bmp, intent.x0, intent.y0, intent.x1, intent.y1, drawColor, size, shape, state.symmetryMode)
                    }
                    DrawEvent.MOVE -> {
                        if (state.selectedTool != Tool.FILL) drawingEngine.drawStroke(bmp, intent.x0, intent.y0, intent.x1, intent.y1, drawColor, size, shape, state.symmetryMode)
                    }
                    DrawEvent.END -> {
                        historyManager.onDrawEnd(state.activeCanvasIndex, activeCanvasObj.activeFrameIndex, bmp, drawingEngine.bounds)
                        updateUndoRedoState()
                        
                        historyManager.lastAction()?.let {
                            viewModelScope.launch(Dispatchers.Default) {
                                val newCode = CodeGenerator.generateDiffToDsl(state.codeContent, it)
                                if (newCode != state.codeContent) _uiState.update { s -> s.copy(codeContent = newCode) }
                            }
                        }
                    }
                }
                _uiState.update { it.copy(redrawTrigger = it.redrawTrigger + 1) }
            }
            is EditorIntent.ToggleSymmetryMode -> _uiState.update { it.copy(symmetryMode = SymmetryMode.values()[(state.symmetryMode.ordinal + 1) % SymmetryMode.values().size]) }
            is EditorIntent.ToggleOnionSkin -> _uiState.update { it.copy(onionSkinEnabled = !it.onionSkinEnabled) }
            is EditorIntent.ToggleCanvasPanel -> _uiState.update { it.copy(isCanvasPanelOpen = !it.isCanvasPanelOpen) }
            is EditorIntent.AddCanvas -> {
                commitSelectionIfNeeded()
                val newBmp = drawingEngine.createClearBitmap(state.canvasWidth, state.canvasHeight, state.background)
                val newCanvas = PixelCanvas(name = "新画布 ${state.canvases.size + 1}", frames = listOf(PixelFrame(bitmap = newBmp)))
                _uiState.update { it.copy(canvases = it.canvases + newCanvas, activeCanvasIndex = it.canvases.size, isFrameStripOpen = false, isPlaying = false) }
            }
            is EditorIntent.SelectCanvas -> { commitSelectionIfNeeded(); _uiState.update { it.copy(activeCanvasIndex = intent.index, isPlaying = false) } }
            is EditorIntent.RenameCanvas -> {
                val newList = state.canvases.toMutableList()
                newList[intent.index] = newList[intent.index].copy(name = intent.newName)
                _uiState.update { it.copy(canvases = newList) }
            }
            is EditorIntent.DuplicateCanvas -> {
                val toDup = state.canvases[intent.index]
                val dupFrames = toDup.frames.map { PixelFrame(bitmap = it.bitmap.copy(it.bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true)) }
                val newCanvas = PixelCanvas(name = "${toDup.name} 副本", frames = dupFrames)
                _uiState.update { it.copy(canvases = it.canvases + newCanvas, activeCanvasIndex = it.canvases.size) }
            }
            is EditorIntent.DeleteCanvas -> {
                commitSelectionIfNeeded()
                val newList = state.canvases.toMutableList().apply { removeAt(intent.index) }
                val newActive = if (state.activeCanvasIndex >= newList.size) newList.size - 1 else state.activeCanvasIndex
                _uiState.update { it.copy(canvases = newList, activeCanvasIndex = newActive, isPlaying = false) }
            }
            is EditorIntent.ToggleFrameStrip -> _uiState.update { it.copy(isFrameStripOpen = !it.isFrameStripOpen) }
            is EditorIntent.AddFrame -> {
                commitSelectionIfNeeded()
                val currentCanvas = state.canvases[state.activeCanvasIndex]
                val newBmp = drawingEngine.createClearBitmap(state.canvasWidth, state.canvasHeight, state.background)
                val newFrames = currentCanvas.frames + PixelFrame(bitmap = newBmp)
                val newCanvas = currentCanvas.copy(frames = newFrames, activeFrameIndex = newFrames.size - 1)
                val newCanvases = state.canvases.toMutableList().apply { set(state.activeCanvasIndex, newCanvas) }
                _uiState.update { it.copy(canvases = newCanvases, redrawTrigger = it.redrawTrigger + 1) }
            }
            is EditorIntent.SelectFrame -> {
                commitSelectionIfNeeded()
                val newCanvas = state.canvases[state.activeCanvasIndex].copy(activeFrameIndex = intent.index)
                val newCanvases = state.canvases.toMutableList().apply { set(state.activeCanvasIndex, newCanvas) }
                _uiState.update { it.copy(canvases = newCanvases, redrawTrigger = it.redrawTrigger + 1) }
            }
            is EditorIntent.ToggleFrameSettings -> _uiState.update { it.copy(isFrameSettingsOpen = !it.isFrameSettingsOpen) }
            is EditorIntent.UpdatePlaybackSettings -> _uiState.update { it.copy(playbackFps = intent.fps, frameHoldTicks = intent.holds) }
            is EditorIntent.TogglePlay -> {
                commitSelectionIfNeeded()
                val willPlay = !state.isPlaying
                _uiState.update { it.copy(isPlaying = willPlay, playingFrameIndex = if (willPlay) 0 else it.canvases[it.activeCanvasIndex].activeFrameIndex) }
                if (willPlay) {
                    playJob = viewModelScope.launch {
                        while(isActive) {
                            delay((1000L / _uiState.value.playbackFps) * _uiState.value.frameHoldTicks)
                            _uiState.update { s ->
                                val canvas = s.canvases[s.activeCanvasIndex]
                                val nextFrame = (s.playingFrameIndex + 1) % canvas.frames.size
                                s.copy(playingFrameIndex = nextFrame, redrawTrigger = s.redrawTrigger + 1)
                            }
                        }
                    }
                } else playJob?.cancel()
            }
            is EditorIntent.UpdateCode -> _uiState.update { it.copy(codeContent = intent.code) }
            is EditorIntent.SyncCodeToCanvas -> {
                commitSelectionIfNeeded()
                val activeCanvas = state.canvases.getOrNull(state.activeCanvasIndex)
                if (activeCanvas != null) {
                    val result = CodeEngine.executeToFrames(state.codeContent, state.canvasWidth, state.canvasHeight)
                    val newFrames = result.frames.map { PixelFrame(bitmap = it) }
                    val newCanvas = activeCanvas.copy(frames = newFrames, activeFrameIndex = 0)
                    val newCanvases = state.canvases.toMutableList().apply { set(state.activeCanvasIndex, newCanvas) }
                    
                    _uiState.update { 
                        it.copy(
                            canvases = newCanvases, 
                            playingFrameIndex = 0, 
                            redrawTrigger = it.redrawTrigger + 1,
                            playbackFps = result.fps ?: it.playbackFps,
                            frameHoldTicks = result.holds ?: it.frameHoldTicks
                        ) 
                    }
                    viewModelScope.launch { _uiEffect.emit(EditorEffect.ShowToast("代码已成功重新编译为 ${newFrames.size} 帧画面")) }
                }
            }
            is EditorIntent.SetToolSize -> { if (intent.tool == Tool.ERASER) _uiState.update { it.copy(eraserSize = intent.size) } else _uiState.update { it.copy(brushSize = intent.size) } }
            is EditorIntent.SetToolShape -> { if (intent.tool == Tool.ERASER) _uiState.update { it.copy(eraserShape = intent.shape) } else _uiState.update { it.copy(brushShape = intent.shape) } }
            is EditorIntent.ToggleColorPalette -> _uiState.update { it.copy(isColorPaletteOpen = !it.isColorPaletteOpen) }
            is EditorIntent.SetColor -> {
                val newRecent = listOf(intent.color) + state.recentColors.filter { c -> c != intent.color }
                _uiState.update { it.copy(currentColor = intent.color, recentColors = newRecent.take(16)) }
            }
            is EditorIntent.PickColorAt -> pickColor(intent.x, intent.y)
            is EditorIntent.SelectTool -> {
                commitSelectionIfNeeded()
                _uiState.update { it.copy(selectedTool = intent.tool) }
            }
            is EditorIntent.SwitchMode -> { commitSelectionIfNeeded(); _uiState.update { it.copy(currentMode = intent.mode) } }
            is EditorIntent.ToggleGrid -> _uiState.update { it.copy(showGrid = !it.showGrid) }
            is EditorIntent.Undo -> { commitSelectionIfNeeded(); if (historyManager.undo(state.canvases)) { updateUndoRedoState(); _uiState.update { it.copy(redrawTrigger = it.redrawTrigger + 1) } } }
            is EditorIntent.Redo -> { commitSelectionIfNeeded(); if (historyManager.redo(state.canvases)) { updateUndoRedoState(); _uiState.update { it.copy(redrawTrigger = it.redrawTrigger + 1) } } }
            is EditorIntent.NavigateBack -> viewModelScope.launch { _uiEffect.emit(EditorEffect.NavigateBack) }
            
            // 拆分后的新保存系统
            is EditorIntent.RequestSaveSingleFrame -> {
                commitSelectionIfNeeded()
                val activeCanvasObj = state.canvases.getOrNull(state.activeCanvasIndex) ?: return
                val activeFrame = activeCanvasObj.frames.getOrNull(activeCanvasObj.activeFrameIndex) ?: return
                val exportFrame = activeFrame.bitmap
                val exportScaledBitmap = drawingEngine.createExportBitmap(exportFrame, state.canvasWidth, state.canvasHeight)
                viewModelScope.launch { _uiEffect.emit(EditorEffect.SaveProject(exportFrame, exportScaledBitmap, state.projectName)) }
            }
            
            is EditorIntent.RequestSaveSpriteSheet -> {
                commitSelectionIfNeeded()
                val frames = state.canvases.getOrNull(state.activeCanvasIndex)?.frames ?: return
                if (frames.isEmpty()) return
                
                val sheetWidth = state.canvasWidth * frames.size
                val sheetHeight = state.canvasHeight
                val sheetBmp = android.graphics.Bitmap.createBitmap(sheetWidth, sheetHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(sheetBmp)
                val paint = android.graphics.Paint().apply { isAntiAlias = false }
                
                for ((index, frame) in frames.withIndex()) {
                    canvas.drawBitmap(frame.bitmap, (index * state.canvasWidth).toFloat(), 0f, paint)
                }
                
                val targetMinSize = 1024
                val scaleMultiplier = kotlin.math.max(1, targetMinSize / kotlin.math.max(sheetWidth, sheetHeight))
                val exportScaledBitmap = android.graphics.Bitmap.createScaledBitmap(sheetBmp, sheetWidth * scaleMultiplier, sheetHeight * scaleMultiplier, false)
                
                viewModelScope.launch { _uiEffect.emit(EditorEffect.SaveProject(sheetBmp, exportScaledBitmap, "${state.projectName}_spritesheet")) }
            }
        }
    }

    private fun handleSelectionAction(intent: EditorIntent.SelectionAction, state: EditorState) {
        val x = intent.x
        val y = intent.y
        when (intent.event) {
            DrawEvent.START -> {
                val rect = state.selectionRect
                val isInside = rect != null && 
                    x >= rect.left + state.floatingOffsetX && x <= rect.right + state.floatingOffsetX &&
                    y >= rect.top + state.floatingOffsetY && y <= rect.bottom + state.floatingOffsetY

                if (isInside) {
                    if (state.floatingBitmap == null && rect != null) {
                        val activeCanvasObj = state.canvases.getOrNull(state.activeCanvasIndex) ?: return
                        val activeFrame = activeCanvasObj.frames.getOrNull(activeCanvasObj.activeFrameIndex) ?: return
                        val bmp = activeFrame.bitmap
                        
                        historyManager.onDrawStart(bmp)
                        val floating = drawingEngine.extractSelection(bmp, rect)
                        _uiState.update { it.copy(floatingBitmap = floating) }
                    }
                    isMovingSelection = true
                    moveStartX = x
                    moveStartY = y
                    initialFloatingOffsetX = state.floatingOffsetX
                    initialFloatingOffsetY = state.floatingOffsetY
                } else {
                    commitSelectionIfNeeded()
                    isSelecting = true
                    selectionStartX = x.coerceIn(0, state.canvasWidth)
                    selectionStartY = y.coerceIn(0, state.canvasHeight)
                    _uiState.update { it.copy(selectionRect = android.graphics.Rect(selectionStartX, selectionStartY, selectionStartX, selectionStartY), floatingOffsetX = 0, floatingOffsetY = 0) }
                }
            }
            DrawEvent.MOVE -> {
                if (isSelecting) {
                    val cx = x.coerceIn(0, state.canvasWidth)
                    val cy = y.coerceIn(0, state.canvasHeight)
                    val l = minOf(selectionStartX, cx)
                    val t = minOf(selectionStartY, cy)
                    val r = maxOf(selectionStartX, cx)
                    val b = maxOf(selectionStartY, cy)
                    _uiState.update { it.copy(selectionRect = android.graphics.Rect(l, t, r, b)) }
                } else if (isMovingSelection) {
                    val dx = x - moveStartX
                    val dy = y - moveStartY
                    _uiState.update { it.copy(floatingOffsetX = initialFloatingOffsetX + dx, floatingOffsetY = initialFloatingOffsetY + dy) }
                }
            }
            DrawEvent.END -> {
                if (isSelecting) {
                    isSelecting = false
                    val rect = state.selectionRect
                    if (rect != null && (rect.width() <= 0 || rect.height() <= 0)) {
                        _uiState.update { it.copy(selectionRect = null) }
                    }
                } else if (isMovingSelection) {
                    isMovingSelection = false
                }
            }
        }
        _uiState.update { it.copy(redrawTrigger = it.redrawTrigger + 1) }
    }

    private fun commitSelectionIfNeeded() {
        if (_uiState.value.selectionRect != null) commitSelection()
    }

    private fun commitSelection() {
        val state = _uiState.value
        if (state.floatingBitmap != null && state.selectionRect != null) {
            val activeCanvasObj = state.canvases.getOrNull(state.activeCanvasIndex) ?: return
            val activeFrame = activeCanvasObj.frames.getOrNull(activeCanvasObj.activeFrameIndex) ?: return
            val bmp = activeFrame.bitmap
            
            val targetX = state.selectionRect.left + state.floatingOffsetX
            val targetY = state.selectionRect.top + state.floatingOffsetY
            
            drawingEngine.mergeSelection(bmp, state.floatingBitmap, targetX, targetY)
            
            val updateRect = android.graphics.Rect(
                min(state.selectionRect.left, targetX),
                min(state.selectionRect.top, targetY),
                max(state.selectionRect.right, targetX + state.floatingBitmap.width),
                max(state.selectionRect.bottom, targetY + state.floatingBitmap.height)
            )
            
            historyManager.onDrawEnd(state.activeCanvasIndex, activeCanvasObj.activeFrameIndex, bmp, updateRect)
            updateUndoRedoState()
            
            historyManager.lastAction()?.let { last ->
                viewModelScope.launch(Dispatchers.Default) {
                    val newCode = CodeGenerator.generateDiffToDsl(state.codeContent, last)
                    if (newCode != state.codeContent) _uiState.update { s -> s.copy(codeContent = newCode) }
                }
            }
        }
        _uiState.update { it.copy(selectionRect = null, floatingBitmap = null, floatingOffsetX = 0, floatingOffsetY = 0, redrawTrigger = it.redrawTrigger + 1) }
    }

    fun triggerToolSubMenu(tool: Tool) {
        viewModelScope.launch { _uiEffect.emit(EditorEffect.ShowToolSubMenu(tool)) }
    }

    private fun updateUndoRedoState() { _uiState.update { it.copy(canUndo = historyManager.canUndo(), canRedo = historyManager.canRedo()) } }
    
    private fun pickColor(x: Int, y: Int) {
        val state = _uiState.value
        if (x !in 0 until state.canvasWidth || y !in 0 until state.canvasHeight) return
        val currentCanvas = state.canvases.getOrNull(state.activeCanvasIndex) ?: return
        val currentFrame = currentCanvas.frames.getOrNull(currentCanvas.activeFrameIndex) ?: return
        val pixelColor = currentFrame.bitmap.getPixel(x, y)
        
        val newColor = Color(pixelColor)
        val newRecent = listOf(newColor) + state.recentColors.filter { c -> c != newColor }
        _uiState.update { it.copy(currentColor = newColor, recentColors = newRecent.take(16)) }
    }
}