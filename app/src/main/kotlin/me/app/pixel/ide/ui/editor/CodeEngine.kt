package me.app.pixel.ide.ui.editor

import android.graphics.Bitmap
import me.app.pixel.ide.ui.editor.dsl.NovaLexer
import me.app.pixel.ide.ui.editor.dsl.NovaParser
import me.app.pixel.ide.ui.editor.dsl.NovaInterpreter

data class RenderResult(val frames: List<Bitmap>, val fps: Int?, val holds: Int?)

object CodeEngine {
    fun executeToFrames(code: String, width: Int, height: Int): RenderResult {
        return try {
            val lexer = NovaLexer(code)
            val tokens = lexer.scanTokens()
            
            val parser = NovaParser(tokens)
            val statements = parser.parse()
            
            val interpreter = NovaInterpreter(width, height)
            val frames = interpreter.execute(statements)
            
            RenderResult(frames, interpreter.outFps, interpreter.outHolds)
        } catch (e: Exception) {
            e.printStackTrace()
            // 解析失败时返回空白帧，但不改变之前的播放设置
            RenderResult(listOf(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)), null, null)
        }
    }
}