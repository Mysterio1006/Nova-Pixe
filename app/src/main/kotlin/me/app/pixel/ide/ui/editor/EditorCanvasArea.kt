package me.app.pixel.ide.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import me.app.pixel.ide.ui.newcanvas.CanvasBg
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EditorCanvasArea(state: EditorState, viewModel: EditorViewModel) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // ==== 动态蚂蚁线系统 (Marching Ants) ====
    val infiniteTransition = rememberInfiniteTransition()
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Restart)
    )

    LaunchedEffect(viewportSize, state.canvasWidth, state.canvasHeight) {
        if (viewportSize.width > 0 && scale == 0f) {
            val fitScaleX = viewportSize.width.toFloat() / state.canvasWidth
            val fitScaleY = viewportSize.height.toFloat() / state.canvasHeight
            scale = min(fitScaleX, fitScaleY) * 0.9f 
            offset = Offset.Zero
        }
    }

    val interactionModifier = Modifier.pointerInput(state.selectedTool, state.isPlaying) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var isTransforming = false
            val screenCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            var lastDrawPx: Pair<Int, Int>? = null

            do {
                val event = awaitPointerEvent()
                val changes = event.changes

                if (changes.size >= 2 || state.selectedTool == Tool.HAND) {
                    isTransforming = true
                    lastDrawPx = null 

                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid()

                    if (zoom != 1f || pan != Offset.Zero) {
                        val oldScale = scale
                        scale = (scale * zoom).coerceIn(0.1f, 1000f)
                        val actualZoom = scale / oldScale
                        
                        val newOffsetX = (centroid.x - screenCenter.x) * (1f - actualZoom) + offset.x * actualZoom + pan.x
                        val newOffsetY = (centroid.y - screenCenter.y) * (1f - actualZoom) + offset.y * actualZoom + pan.y
                        offset = Offset(newOffsetX, newOffsetY)
                        
                        changes.forEach { it.consume() }
                    }
                } else if (changes.size == 1 && !isTransforming && state.selectedTool != Tool.HAND && !state.isPlaying) {
                    val change = changes.first()
                    val drawWidth = state.canvasWidth * scale
                    val drawHeight = state.canvasHeight * scale
                    val topLeftX = screenCenter.x - drawWidth / 2f + offset.x
                    val topLeftY = screenCenter.y - drawHeight / 2f + offset.y

                    val px = ((change.position.x - topLeftX) / scale).toInt()
                    val py = ((change.position.y - topLeftY) / scale).toInt()

                    if (state.selectedTool == Tool.SELECT) {
                        if (lastDrawPx == null) viewModel.processIntent(EditorIntent.SelectionAction(px, py, DrawEvent.START))
                        else if (lastDrawPx!!.first != px || lastDrawPx!!.second != py) viewModel.processIntent(EditorIntent.SelectionAction(px, py, DrawEvent.MOVE))
                    } else {
                        if (lastDrawPx == null) viewModel.processIntent(EditorIntent.DrawAction(px, py, px, py, DrawEvent.START))
                        else if (lastDrawPx!!.first != px || lastDrawPx!!.second != py) viewModel.processIntent(EditorIntent.DrawAction(lastDrawPx!!.first, lastDrawPx!!.second, px, py, DrawEvent.MOVE))
                    }
                    
                    lastDrawPx = px to py
                    change.consume()
                }
            } while (changes.any { it.pressed })
            
            if (lastDrawPx != null && !isTransforming && !state.isPlaying) {
                if (state.selectedTool == Tool.SELECT) viewModel.processIntent(EditorIntent.SelectionAction(lastDrawPx!!.first, lastDrawPx!!.second, DrawEvent.END))
                else viewModel.processIntent(EditorIntent.DrawAction(lastDrawPx!!.first, lastDrawPx!!.second, lastDrawPx!!.first, lastDrawPx!!.second, DrawEvent.END))
            }
        }
    }

    val checkerboardBitmap = remember(state.canvasWidth, state.canvasHeight) {
        val bmp = Bitmap.createBitmap(state.canvasWidth, state.canvasHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(state.canvasWidth * state.canvasHeight)
        val c1 = android.graphics.Color.WHITE
        val c2 = android.graphics.Color.LTGRAY
        for (y in 0 until state.canvasHeight) {
            for (x in 0 until state.canvasWidth) {
                pixels[y * state.canvasWidth + x] = if ((x + y) % 2 == 0) c1 else c2
            }
        }
        bmp.setPixels(pixels, 0, state.canvasWidth, 0, 0, state.canvasWidth, state.canvasHeight)
        bmp.asImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it }.then(if (viewportSize.width > 0) interactionModifier else Modifier)) {
        if (viewportSize.width > 0 && scale > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                val drawWidth = state.canvasWidth * scale
                val drawHeight = state.canvasHeight * scale
                val topLeftX = screenCenter.x - drawWidth / 2f + offset.x
                val topLeftY = screenCenter.y - drawHeight / 2f + offset.y

                if (state.background == CanvasBg.TRANSPARENT) {
                    drawImage(checkerboardBitmap, dstOffset = IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()), dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()), filterQuality = FilterQuality.None)
                } else {
                    drawRect(color = if (state.background == CanvasBg.WHITE) Color.White else Color.Black, topLeft = Offset(topLeftX, topLeftY), size = androidx.compose.ui.geometry.Size(drawWidth, drawHeight))
                }

                val activeCanvas = state.canvases.getOrNull(state.activeCanvasIndex)
                
                if (activeCanvas != null) {
                    if (state.onionSkinEnabled && !state.isPlaying && activeCanvas.activeFrameIndex > 0) {
                        val prevFrame = activeCanvas.frames.getOrNull(activeCanvas.activeFrameIndex - 1)
                        if (prevFrame != null) drawImage(image = prevFrame.bitmap.asImageBitmap(), dstOffset = IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()), dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()), alpha = 0.35f, filterQuality = FilterQuality.None)
                    }

                    val targetFrameIndex = if (state.isPlaying) state.playingFrameIndex else activeCanvas.activeFrameIndex
                    val frameToDraw = activeCanvas.frames.getOrNull(targetFrameIndex)
                    
                    if (frameToDraw != null) drawImage(frameToDraw.bitmap.asImageBitmap(), dstOffset = IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()), dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()), filterQuality = FilterQuality.None)
                }

                // ===============================================
                // == 选区高级渲染：浮动图层与动态黑白双层蚂蚁线 ==
                // ===============================================
                if (state.selectionRect != null) {
                    val rect = state.selectionRect
                    val selTopLeftX = topLeftX + (rect.left + state.floatingOffsetX) * scale
                    val selTopLeftY = topLeftY + (rect.top + state.floatingOffsetY) * scale
                    val selWidth = rect.width() * scale
                    val selHeight = rect.height() * scale
                    
                    // 1. 如果处于提取状态，绘制悬空的像素块
                    if (state.floatingBitmap != null) {
                        drawImage(
                            image = state.floatingBitmap.asImageBitmap(),
                            dstOffset = IntOffset(selTopLeftX.roundToInt(), selTopLeftY.roundToInt()),
                            dstSize = IntSize(selWidth.roundToInt(), selHeight.roundToInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                    
                    // 2. 绘制会流动的极美双层虚线选框 (白底+黑缝，无视背景颜色均清晰可见)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(selTopLeftX, selTopLeftY),
                        size = androidx.compose.ui.geometry.Size(selWidth, selHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2f, 
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), dashPhase)
                        )
                    )
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(selTopLeftX, selTopLeftY),
                        size = androidx.compose.ui.geometry.Size(selWidth, selHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2f, 
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), dashPhase + 15f)
                        )
                    )
                }

                if (state.symmetryMode != SymmetryMode.NONE && scale > 3f) {
                    val axisColor = Color.Cyan.copy(alpha = 0.6f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                    if (state.symmetryMode == SymmetryMode.HORIZONTAL || state.symmetryMode == SymmetryMode.QUAD) {
                        val midX = topLeftX + drawWidth / 2f
                        drawLine(axisColor, Offset(midX, topLeftY), Offset(midX, topLeftY + drawHeight), strokeWidth = 2f, pathEffect = dashEffect)
                    }
                    if (state.symmetryMode == SymmetryMode.VERTICAL || state.symmetryMode == SymmetryMode.QUAD) {
                        val midY = topLeftY + drawHeight / 2f
                        drawLine(axisColor, Offset(topLeftX, midY), Offset(topLeftX + drawWidth, midY), strokeWidth = 2f, pathEffect = dashEffect)
                    }
                }

                if (state.showGrid && scale >= 6f) drawPixelGrid(state, scale, topLeftX, topLeftY, drawWidth, drawHeight)
            }
        }
    }
}

private fun DrawScope.drawPixelGrid(state: EditorState, scale: Float, topLeftX: Float, topLeftY: Float, drawWidth: Float, drawHeight: Float) {
    val gridColor = Color.Gray.copy(alpha = 0.4f)
    val strokeW = if (scale > 20f) 2f else 1f 
    val startXCol = kotlin.math.max(0, ((0f - topLeftX) / scale).toInt())
    val endXCol = kotlin.math.min(state.canvasWidth, ((size.width - topLeftX) / scale).toInt() + 1)
    for (i in startXCol..endXCol) drawLine(gridColor, Offset(topLeftX + i * scale, topLeftY), Offset(topLeftX + i * scale, topLeftY + drawHeight), strokeWidth = strokeW)
    val startYRow = kotlin.math.max(0, ((0f - topLeftY) / scale).toInt())
    val endYRow = kotlin.math.min(state.canvasHeight, ((size.height - topLeftY) / scale).toInt() + 1)
    for (i in startYRow..endYRow) drawLine(gridColor, Offset(topLeftX, topLeftY + i * scale), Offset(topLeftX + drawWidth, topLeftY + i * scale), strokeWidth = strokeW)
    drawRect(color = Color.DarkGray, topLeft = Offset(topLeftX, topLeftY), size = androidx.compose.ui.geometry.Size(drawWidth, drawHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW * 2))
}