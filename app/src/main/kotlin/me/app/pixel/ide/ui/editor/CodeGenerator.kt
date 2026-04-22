package me.app.pixel.ide.ui.editor

import android.graphics.Color

object CodeGenerator {

    fun generateDiffToDsl(currentCode: String, lastAction: HistoryAction): String {
        val w = lastAction.width
        val h = lastAction.height
        val offsetX = lastAction.left
        val offsetY = lastAction.top
        
        val oldPixels = IntArray(w * h)
        val newPixels = IntArray(w * h)
        lastAction.oldChunk.getPixels(oldPixels, 0, w, 0, 0, w, h)
        lastAction.newChunk.getPixels(newPixels, 0, w, 0, 0, w, h)

        val varMap = mutableMapOf<Int, String>()
        currentCode.lines().forEach { line ->
            val t = line.trim()
            if (t.contains("=") && !t.contains("(")) {
                val parts = t.split("=", limit = 2)
                if (parts.size == 2) {
                    try { 
                        varMap[Color.parseColor(parts[1].trim())] = parts[0].trim() 
                    } catch (e: Exception) {}
                }
            }
        }
        
        // 核心改动：不再添加双引号，直接返回纯净的 #HEX
        fun formatColor(c: Int): String {
            if (varMap.containsKey(c)) return varMap[c]!! 
            val a = Color.alpha(c)
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            return if (a == 255) String.format("#%02X%02X%02X", r, g, b) 
            else String.format("#%02X%02X%02X%02X", a, r, g, b)
        }
        
        val sb = StringBuilder()
        val visited = BooleanArray(w * h) { false }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (visited[idx] || oldPixels[idx] == newPixels[idx]) continue
                
                val targetColor = newPixels[idx]
                val isErase = Color.alpha(targetColor) == 0

                var currentW = 1
                while (x + currentW < w) {
                    val nextIdx = y * w + (x + currentW)
                    if (!visited[nextIdx] && oldPixels[nextIdx] != newPixels[nextIdx] && newPixels[nextIdx] == targetColor) currentW++
                    else break
                }

                var currentH = 1
                var canExpandY = true
                while (y + currentH < h && canExpandY) {
                    for (ix in 0 until currentW) {
                        val rowIdx = (y + currentH) * w + (x + ix)
                        if (visited[rowIdx] || oldPixels[rowIdx] == newPixels[rowIdx] || newPixels[rowIdx] != targetColor) {
                            canExpandY = false
                            break
                        }
                    }
                    if (canExpandY) currentH++
                }

                for (iy in 0 until currentH) {
                    for (ix in 0 until currentW) visited[(y + iy) * w + (x + ix)] = true
                }

                val absX = x + offsetX
                val absY = y + offsetY
                
                if (isErase) {
                    if (currentW == 1 && currentH == 1) sb.append("erase($absX, $absY)\n")
                    else sb.append("erase($absX, $absY, $currentW, $currentH)\n")
                } else {
                    val cStr = formatColor(targetColor)
                    if (currentW == 1 && currentH == 1) sb.append("dot($absX, $absY, $cStr)\n")
                    else sb.append("rect($absX, $absY, $currentW, $currentH, $cStr)\n")
                }
            }
        }
        
        val generated = sb.toString()
        if (generated.isEmpty()) return currentCode

        val currentLines = currentCode.lines().toMutableList()
        var insertIndex = currentLines.size
        
        for (i in currentLines.indices.reversed()) {
            val cmd = currentLines[i].trim().substringBefore("(").trim()
            val validCmds = setOf("rect", "circle", "dot", "erase", "stamp", "shiftDraw", "load", "save", "movePixels", "copyFrame", "newFrame", "flipX", "flipY")
            if (cmd in validCmds) {
                insertIndex = i + 1
                break
            }
        }
        
        val indent = if (insertIndex > 0 && insertIndex <= currentLines.size) {
            currentLines[insertIndex - 1].takeWhile { it == ' ' || it == '\t' } 
        } else ""
        
        currentLines.add(insertIndex, generated.trimEnd().lines().joinToString("\n") { indent + it })
        return currentLines.joinToString("\n")
    }
}