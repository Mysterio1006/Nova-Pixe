package me.app.pixel.ide.ui.editor.dsl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

class NovaInterpreter(
    val width: Int,
    val height: Int,
    private val globalSavedSprites: MutableMap<String, Bitmap> = mutableMapOf()
) {
    private val environment = mutableMapOf<String, Any>()
    private val macros = mutableMapOf<String, Stmt.MacroDef>()
    private val mirrorXStack = mutableListOf<Int>()
    private val mirrorYStack = mutableListOf<Int>()
    
    private val frames = mutableListOf<Bitmap>()
    private var currentBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private var canvas = Canvas(currentBmp)
    
    private var offsetX: Int = 0
    private var offsetY: Int = 0

    var outFps: Int? = null
    var outHolds: Int? = null

    fun execute(statements: List<Stmt>): List<Bitmap> {
        frames.clear()
        macros.clear()
        mirrorXStack.clear()
        mirrorYStack.clear()
        offsetX = 0
        offsetY = 0
        
        environment["W"] = width
        environment["H"] = height
        environment["w"] = width
        environment["h"] = height

        outFps = null
        outHolds = null

        try {
            for (stmt in statements) executeStmt(stmt)
        } catch (e: Exception) { e.printStackTrace() }
        frames.add(currentBmp)
        return frames
    }

    private fun executeStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Assign -> environment[stmt.name] = evaluate(stmt.value)
            is Stmt.Block -> stmt.statements.forEach { executeStmt(it) }
            is Stmt.Repeat -> {
                val times = (evaluate(stmt.times) as? Int) ?: 0
                val varName = stmt.varName ?: "i"
                
                val prevVar = environment[varName]
                
                for (i in 0 until times) {
                    environment[varName] = i 
                    executeStmt(stmt.body)
                }
                
                if (prevVar != null) {
                    environment[varName] = prevVar
                } else {
                    environment.remove(varName)
                }
            }
            is Stmt.Offset -> {
                val dx = (evaluate(stmt.dx) as? Int) ?: 0
                val dy = (evaluate(stmt.dy) as? Int) ?: 0
                val prevX = offsetX
                val prevY = offsetY
                
                offsetX += dx
                offsetY += dy
                executeStmt(stmt.body)
                offsetX = prevX
                offsetY = prevY
            }
            is Stmt.If -> {
                val cond = evaluate(stmt.condition)
                val isTrue = when (cond) {
                    is Boolean -> cond
                    is Int -> cond != 0
                    else -> false
                }
                if (isTrue) {
                    executeStmt(stmt.thenBranch)
                } else if (stmt.elseBranch != null) {
                    executeStmt(stmt.elseBranch)
                }
            }
            is Stmt.MacroDef -> macros[stmt.name] = stmt
            is Stmt.Mirror -> {
                val axisVal = (evaluate(stmt.axis) as? Int) ?: 0
                if (stmt.isX) mirrorXStack.add(axisVal) else mirrorYStack.add(axisVal)
                executeStmt(stmt.body)
                if (stmt.isX) mirrorXStack.removeLast() else mirrorYStack.removeLast()
            }
            is Stmt.FuncCall -> executeFunction(stmt.name, stmt.args)
        }
    }

    private fun executeFunction(name: String, args: List<Expr>) {
        val evalArgs = args.map { evaluate(it) }
        
        // 动作宏调用
        if (macros.containsKey(name)) {
            val macro = macros[name]!!
            if (macro.body != null) {
                val prevEnv = mutableMapOf<String, Any?>()
                macro.params.forEachIndexed { index, paramName ->
                    prevEnv[paramName] = environment[paramName]
                    environment[paramName] = evalArgs.getOrElse(index) { 0 }
                }
                executeStmt(macro.body)
                macro.params.forEach { paramName ->
                    val prev = prevEnv[paramName]
                    if (prev == null) environment.remove(paramName) else environment[paramName] = prev
                }
                return
            }
        }

        fun getInt(index: Int, default: Int = 0): Int = (evalArgs.getOrNull(index) as? Int) ?: default
        fun getString(index: Int, default: String = ""): String = (evalArgs.getOrNull(index)?.toString()) ?: default
        fun resolveColor(obj: Any?): Int {
            val colorStr = (obj as? String) ?: return Color.TRANSPARENT
            return try { Color.parseColor(colorStr) } catch (e: Exception) { Color.TRANSPARENT }
        }

        val erasePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val drawPaint = Paint().apply { isAntiAlias = false } 

        when (name) {
            "setFps" -> outFps = getInt(0, 24).coerceIn(1, 60)
            "setHolds" -> outHolds = getInt(0, 3).coerceIn(1, 10)

            "copyFrame" -> { frames.add(currentBmp); currentBmp = currentBmp.copy(currentBmp.config ?: Bitmap.Config.ARGB_8888, true); canvas = Canvas(currentBmp) }
            "newFrame" -> { frames.add(currentBmp); currentBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); canvas = Canvas(currentBmp) }
            "movePixels" -> { 
                val temp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(temp).drawBitmap(currentBmp, getInt(0).toFloat(), getInt(1).toFloat(), drawPaint)
                currentBmp = temp; canvas = Canvas(currentBmp) 
            }
            "shiftDraw" -> { offsetX = getInt(0); offsetY = getInt(1) }
            // save/load/stamp 现在天然支持传入字符串表达式，如 "frame_" + i
            "save" -> globalSavedSprites[getString(0)] = currentBmp.copy(currentBmp.config ?: Bitmap.Config.ARGB_8888, false)
            "load" -> globalSavedSprites[getString(0)]?.let { canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); canvas.drawBitmap(it, 0f, 0f, drawPaint) }
            "flipX" -> { val m = Matrix().apply { preScale(-1f, 1f); postTranslate(width.toFloat(), 0f) }; val temp = Bitmap.createBitmap(currentBmp, 0, 0, width, height, m, false); canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); canvas.drawBitmap(temp, 0f, 0f, drawPaint); temp.recycle() }
            "flipY" -> { val m = Matrix().apply { preScale(1f, -1f); postTranslate(0f, height.toFloat()) }; val temp = Bitmap.createBitmap(currentBmp, 0, 0, width, height, m, false); canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); canvas.drawBitmap(temp, 0f, 0f, drawPaint); temp.recycle() }
            "clear" -> canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            
            "rect", "fillRegion" -> {
                drawPaint.color = resolveColor(evalArgs.getOrNull(4))
                drawRectMirrored(getInt(0) + offsetX, getInt(1) + offsetY, getInt(2), getInt(3), drawPaint)
            }
            "dot" -> {
                drawPaint.color = resolveColor(evalArgs.getOrNull(2))
                drawRectMirrored(getInt(0) + offsetX, getInt(1) + offsetY, 1, 1, drawPaint)
            }
            "circle", "drawCircle" -> {
                drawPaint.color = resolveColor(evalArgs.getOrNull(3))
                drawCircleMirrored(getInt(0) + offsetX, getInt(1) + offsetY, getInt(2), drawPaint)
            }
            "erase" -> drawRectMirrored(getInt(0) + offsetX, getInt(1) + offsetY, getInt(2, 1), getInt(3, 1), erasePaint)
            "stamp" -> stampMirrored(getString(0), getInt(1) + offsetX, getInt(2) + offsetY, drawPaint)
            
            "line" -> {
                val x1 = getInt(0) + offsetX
                val y1 = getInt(1) + offsetY
                val x2 = getInt(2) + offsetX
                val y2 = getInt(3) + offsetY
                drawPaint.color = resolveColor(evalArgs.getOrNull(4))

                var x = x1
                var y = y1
                val dx = kotlin.math.abs(x2 - x1)
                val dy = kotlin.math.abs(y2 - y1)
                val sx = if (x1 < x2) 1 else -1
                val sy = if (y1 < y2) 1 else -1
                var err = dx - dy

                while (true) {
                    drawRectMirrored(x, y, 1, 1, drawPaint)
                    if (x == x2 && y == y2) break
                    val e2 = 2 * err
                    if (e2 > -dy) { err -= dy; x += sx }
                    if (e2 < dx) { err += dx; y += sy }
                }
            }
        }
    }
    
    private fun drawRectMirrored(x: Int, y: Int, w: Int, h: Int, paint: Paint) {
        val mx = mirrorXStack.lastOrNull()
        val my = mirrorYStack.lastOrNull()
        val xs = if (mx != null) listOf(x, mx + (mx - x) - w + 1).distinct() else listOf(x)
        val ys = if (my != null) listOf(y, my + (my - y) - h + 1).distinct() else listOf(y)
        for (cx in xs) {
            for (cy in ys) {
                canvas.drawRect(cx.toFloat(), cy.toFloat(), (cx + w).toFloat(), (cy + h).toFloat(), paint)
            }
        }
    }

    private fun drawCircleMirrored(cx: Int, cy: Int, r: Int, paint: Paint) {
        val mx = mirrorXStack.lastOrNull()
        val my = mirrorYStack.lastOrNull()
        val xs = if (mx != null) listOf(cx, mx + (mx - cx)).distinct() else listOf(cx)
        val ys = if (my != null) listOf(cy, my + (my - cy)).distinct() else listOf(cy)
        for (x in xs) {
            for (y in ys) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), paint)
            }
        }
    }

    private fun stampMirrored(name: String, x: Int, y: Int, paint: Paint) {
        val bmp = globalSavedSprites[name] ?: return
        val w = bmp.width
        val h = bmp.height
        val mx = mirrorXStack.lastOrNull()
        val my = mirrorYStack.lastOrNull()
        
        for (flipX in listOf(false, true)) {
            if (flipX && mx == null) continue
            for (flipY in listOf(false, true)) {
                if (flipY && my == null) continue
                
                val cx = if (flipX) mx!! + (mx - x) - w + 1 else x
                val cy = if (flipY) my!! + (my - y) - h + 1 else y
                
                if (flipX || flipY) {
                    val matrix = Matrix().apply {
                        preScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
                        postTranslate(if (flipX) w.toFloat() else 0f, if (flipY) h.toFloat() else 0f)
                        postTranslate(cx.toFloat(), cy.toFloat())
                    }
                    canvas.drawBitmap(bmp, matrix, paint)
                } else {
                    canvas.drawBitmap(bmp, cx.toFloat(), cy.toFloat(), paint)
                }
            }
        }
    }

    private fun evaluate(expr: Expr): Any {
        return when (expr) {
            is Expr.NumLiteral -> expr.value
            is Expr.ColorLiteral -> expr.hex 
            is Expr.StrLiteral -> expr.value
            is Expr.Variable -> environment[expr.name] ?: throw RuntimeException("未定义的变量: ${expr.name}")
            
            is Expr.ListLiteral -> expr.elements.map { evaluate(it) }
            
            is Expr.IndexAccess -> {
                val list = evaluate(expr.list) as? List<*> ?: throw RuntimeException("只能对列表进行[] 索引访问")
                val index = evaluate(expr.index) as? Int ?: throw RuntimeException("列表索引必须是整数 (Int)")
                if (index < 0 || index >= list.size) throw RuntimeException("列表索引越界: 试图访问 $index, 列表长度为 ${list.size}")
                list[index] ?: 0
            }
            
            is Expr.FuncCall -> {
                val evalArgs = expr.args.map { evaluate(it) }
                
                // 处理计算宏调用 (有返回值的宏)
                if (macros.containsKey(expr.name)) {
                    val macro = macros[expr.name]!!
                    if (macro.returnExpr != null) {
                        val prevEnv = mutableMapOf<String, Any?>()
                        macro.params.forEachIndexed { index, paramName ->
                            prevEnv[paramName] = environment[paramName]
                            environment[paramName] = evalArgs.getOrElse(index) { 0 }
                        }
                        val result = evaluate(macro.returnExpr)
                        macro.params.forEach { paramName ->
                            val prev = prevEnv[paramName]
                            if (prev == null) environment.remove(paramName) else environment[paramName] = prev
                        }
                        return result
                    }
                }

                // 内置核心数学函数
                when (expr.name) {
                    "len" -> (evalArgs.getOrNull(0) as? List<*>)?.size ?: throw RuntimeException("len() 必须传入列表")
                    "abs" -> kotlin.math.abs(evalArgs.getOrNull(0) as? Int ?: 0)
                    "max" -> kotlin.math.max(evalArgs.getOrNull(0) as? Int ?: 0, evalArgs.getOrNull(1) as? Int ?: 0)
                    "min" -> kotlin.math.min(evalArgs.getOrNull(0) as? Int ?: 0, evalArgs.getOrNull(1) as? Int ?: 0)
                    "clamp" -> {
                        val v = evalArgs.getOrNull(0) as? Int ?: 0
                        val min = evalArgs.getOrNull(1) as? Int ?: 0
                        val max = evalArgs.getOrNull(2) as? Int ?: 0
                        v.coerceIn(min, max)
                    }
                    else -> throw RuntimeException("未知的表达式函数: ${expr.name}")
                }
            }

            is Expr.Unary -> {
                val right = evaluate(expr.right) as? Int ?: throw RuntimeException("单目操作数必须是数字")
                when (expr.operator.type) {
                    TokenType.MINUS -> -right
                    else -> throw RuntimeException("未知的单目运算符: ${expr.operator.lexeme}")
                }
            }
            
            is Expr.Binary -> {
                val leftRaw = evaluate(expr.left)
                val rightRaw = evaluate(expr.right)
                
                if (expr.operator.type in listOf(TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)) {
                    val leftInt = leftRaw as? Int ?: 0
                    val rightInt = rightRaw as? Int ?: 0
                    return when (expr.operator.type) {
                        TokenType.EQ -> leftRaw == rightRaw
                        TokenType.NEQ -> leftRaw != rightRaw
                        TokenType.LT -> leftInt < rightInt
                        TokenType.GT -> leftInt > rightInt
                        TokenType.LTE -> leftInt <= rightInt
                        TokenType.GTE -> leftInt >= rightInt
                        else -> false
                    }
                }

                if (expr.operator.type == TokenType.PLUS && (leftRaw is String || rightRaw is String)) {
                    return leftRaw.toString() + rightRaw.toString()
                }

                val left = leftRaw as? Int ?: throw RuntimeException("数学运算左侧必须是数字")
                val right = rightRaw as? Int ?: throw RuntimeException("数学运算右侧必须是数字")
                when (expr.operator.type) {
                    TokenType.PLUS -> left + right
                    TokenType.MINUS -> left - right
                    TokenType.STAR -> left * right
                    TokenType.SLASH -> if (right == 0) 0 else left / right
                    TokenType.PERCENT -> if (right == 0) 0 else left % right
                    else -> throw RuntimeException("未知的运算符: ${expr.operator.lexeme}")
                }
            }
        }
    }
}