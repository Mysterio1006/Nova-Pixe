package me.app.pixel.ide.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import me.app.pixel.ide.ui.newcanvas.CanvasBg
import java.util.Stack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DrawingEngine {
    val bounds = Rect()

    fun resetBounds() {
        bounds.set(Int.MAX_VALUE, Int.MAX_VALUE, -1, -1)
    }

    // === 新增：选区提取与合并引擎 ===
    fun extractSelection(bmp: Bitmap, rect: Rect): Bitmap? {
        val l = max(0, rect.left)
        val t = max(0, rect.top)
        val r = min(bmp.width, rect.right)
        val b = min(bmp.height, rect.bottom)
        
        // 如果越界或选区为空，终止提取
        if (l >= r || t >= b) return null
        
        // 提取范围内的像素形成独立浮动图层
        val floating = Bitmap.createBitmap(bmp, l, t, r - l, b - t).copy(Bitmap.Config.ARGB_8888, true)
        
        // 擦除原图层中的被选中区域
        val canvas = Canvas(bmp)
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
        
        return floating
    }

    fun mergeSelection(bmp: Bitmap, floating: Bitmap, x: Int, y: Int) {
        val canvas = Canvas(bmp)
        canvas.drawBitmap(floating, x.toFloat(), y.toFloat(), null)
    }
    // =============================

    fun createClearBitmap(width: Int, height: Int, bg: CanvasBg): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (bg == CanvasBg.WHITE) bmp.eraseColor(android.graphics.Color.WHITE)
        if (bg == CanvasBg.BLACK) bmp.eraseColor(android.graphics.Color.BLACK)
        return bmp
    }

    fun createExportBitmap(exportFrame: Bitmap, canvasWidth: Int, canvasHeight: Int): Bitmap {
        val targetMinSize = 1024
        val scaleMultiplier = max(1, targetMinSize / max(canvasWidth, canvasHeight))
        return Bitmap.createScaledBitmap(exportFrame, canvasWidth * scaleMultiplier, canvasHeight * scaleMultiplier, false)
    }

    private fun setPixel(bmp: Bitmap, x: Int, y: Int, color: Int) {
        if (x in 0 until bmp.width && y in 0 until bmp.height) {
            bmp.setPixel(x, y, color)
            if (x < bounds.left) bounds.left = x
            if (x > bounds.right) bounds.right = x
            if (y < bounds.top) bounds.top = y
            if (y > bounds.bottom) bounds.bottom = y
        }
    }

    fun drawStroke(bmp: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, size: Int, shape: BrushShape, symmetry: SymmetryMode) {
        fun stampSingle(cx: Int, cy: Int) {
            if (size <= 1) {
                setPixel(bmp, cx, cy, color)
                return
            }
            val half = size / 2
            val radiusSq = half * half
            for (x in cx - half..cx + half) {
                for (y in cy - half..cy + half) {
                    if (shape == BrushShape.CIRCLE) {
                        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radiusSq) setPixel(bmp, x, y, color)
                    } else {
                        setPixel(bmp, x, y, color)
                    }
                }
            }
        }

        fun stampSymmetric(cx: Int, cy: Int) {
            stampSingle(cx, cy)
            val mx = bmp.width - 1 - cx
            val my = bmp.height - 1 - cy
            
            if (symmetry == SymmetryMode.HORIZONTAL || symmetry == SymmetryMode.QUAD) stampSingle(mx, cy)
            if (symmetry == SymmetryMode.VERTICAL || symmetry == SymmetryMode.QUAD) stampSingle(cx, my)
            if (symmetry == SymmetryMode.QUAD) stampSingle(mx, my)
        }

        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            stampSymmetric(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
        }
    }

    fun performFloodFill(bmp: Bitmap, startX: Int, startY: Int, replacementColor: Int) {
        if (startX !in 0 until bmp.width || startY !in 0 until bmp.height) return
        val targetColor = bmp.getPixel(startX, startY)
        if (targetColor == replacementColor) return

        data class Point(val x: Int, val y: Int)
        val stack = Stack<Point>()
        stack.push(Point(startX, startY))

        while (stack.isNotEmpty()) {
            val p = stack.pop()
            var cx = p.x
            val cy = p.y
            
            while (cx > 0 && bmp.getPixel(cx - 1, cy) == targetColor) cx--
            
            var spanAbove = false
            var spanBelow = false
            
            while (cx < bmp.width && bmp.getPixel(cx, cy) == targetColor) {
                setPixel(bmp, cx, cy, replacementColor)
                
                if (cy > 0) {
                    val colorAbove = bmp.getPixel(cx, cy - 1)
                    if (!spanAbove && colorAbove == targetColor) {
                        stack.push(Point(cx, cy - 1))
                        spanAbove = true
                    } else if (spanAbove && colorAbove != targetColor) spanAbove = false
                }
                
                if (cy < bmp.height - 1) {
                    val colorBelow = bmp.getPixel(cx, cy + 1)
                    if (!spanBelow && colorBelow == targetColor) {
                        stack.push(Point(cx, cy + 1))
                        spanBelow = true
                    } else if (spanBelow && colorBelow != targetColor) spanBelow = false
                }
                cx++
            }
        }
    }
}