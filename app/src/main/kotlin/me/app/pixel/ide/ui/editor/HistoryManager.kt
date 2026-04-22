package me.app.pixel.ide.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

data class HistoryAction(
    val canvasIndex: Int,
    val frameIndex: Int,
    val left: Int, val top: Int, val width: Int, val height: Int,
    val oldChunk: Bitmap, val newChunk: Bitmap
)

class HistoryManager(private val maxHistorySteps: Int = 30) {
    private val undoStack = ArrayDeque<HistoryAction>()
    private val redoStack = ArrayDeque<HistoryAction>()
    private var backupBitmap: Bitmap? = null

    fun onDrawStart(currentFrameBitmap: Bitmap) {
        backupBitmap?.recycle()
        backupBitmap = currentFrameBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun onDrawEnd(canvasIndex: Int, frameIndex: Int, currentFrameBitmap: Bitmap, modifiedBounds: Rect) {
        // 安全检查：如果未绘制任何内容，Bounds将维持在极大/极小值
        if (modifiedBounds.left == Int.MAX_VALUE || modifiedBounds.left > modifiedBounds.right || modifiedBounds.top > modifiedBounds.bottom) {
            backupBitmap?.recycle()
            backupBitmap = null
            return
        }
        
        val backup = backupBitmap ?: return

        val l = max(0, modifiedBounds.left - 1)
        val t = max(0, modifiedBounds.top - 1)
        val r = min(currentFrameBitmap.width, modifiedBounds.right + 2)
        val b = min(currentFrameBitmap.height, modifiedBounds.bottom + 2)
        val w = r - l
        val h = b - t

        if (w <= 0 || h <= 0) {
            backupBitmap?.recycle()
            backupBitmap = null
            return
        }

        val oldChunk = Bitmap.createBitmap(backup, l, t, w, h).let { 
            if (it === backup) it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) else it 
        }
        val newChunk = Bitmap.createBitmap(currentFrameBitmap, l, t, w, h).let { 
            if (it === currentFrameBitmap) it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) else it 
        }

        undoStack.addLast(HistoryAction(canvasIndex, frameIndex, l, t, w, h, oldChunk, newChunk))
        
        if (undoStack.size > maxHistorySteps) {
            val removed = undoStack.removeFirst()
            removed.oldChunk.recycle()
            removed.newChunk.recycle()
        }

        clearRedoStack()
        backupBitmap?.recycle()
        backupBitmap = null
    }

    fun undo(canvases: List<PixelCanvas>): Boolean {
        if (undoStack.isEmpty()) return false
        val action = undoStack.removeLast()
        
        val canvasObj = canvases.getOrNull(action.canvasIndex) ?: return false
        val frame = canvasObj.frames.getOrNull(action.frameIndex) ?: return false

        val canvas = Canvas(frame.bitmap)
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        canvas.drawBitmap(action.oldChunk, action.left.toFloat(), action.top.toFloat(), paint)

        redoStack.addLast(action)
        return true
    }

    fun redo(canvases: List<PixelCanvas>): Boolean {
        if (redoStack.isEmpty()) return false
        val action = redoStack.removeLast()
        
        val canvasObj = canvases.getOrNull(action.canvasIndex) ?: return false
        val frame = canvasObj.frames.getOrNull(action.frameIndex) ?: return false

        val canvas = Canvas(frame.bitmap)
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        canvas.drawBitmap(action.newChunk, action.left.toFloat(), action.top.toFloat(), paint)

        undoStack.addLast(action)
        return true
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    
    fun lastAction(): HistoryAction? = undoStack.lastOrNull()

    private fun clearRedoStack() {
        redoStack.forEach { it.oldChunk.recycle(); it.newChunk.recycle() }
        redoStack.clear()
    }
}