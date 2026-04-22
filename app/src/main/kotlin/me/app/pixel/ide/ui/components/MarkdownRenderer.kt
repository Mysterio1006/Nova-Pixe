// app/src/main/kotlin/me/app/pixel/ide/ui/components/MarkdownRenderer.kt
package me.app.pixel.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MDBlock {
    data class Heading(val level: Int, val text: String) : MDBlock()
    data class Code(val content: String) : MDBlock()
    data class Quote(val text: String) : MDBlock()
    data class ListItem(val text: String) : MDBlock()
    object Rule : MDBlock()
    data class Paragraph(val text: String) : MDBlock()
}

@Composable
fun MarkdownRenderer(modifier: Modifier = Modifier, markdown: String) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MDBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 24.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                is MDBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = block.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
                is MDBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                is MDBlock.ListItem -> {
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        Text("•", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        Text(text = parseInlineMarkdown(block.text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MDBlock.Rule -> {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                is MDBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// 块级解析器（完全支持多行与内部强制换行）
private fun parseMarkdown(markdown: String): List<MDBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MDBlock>()
    var i = 0
    val blockStartRegex = Regex("^(#|---|```|> |- |\\* )")

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> {
                i++
            }
            line.startsWith("```") -> {
                i++
                val codeContent = java.lang.StringBuilder()
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeContent.appendLine(lines[i])
                    i++
                }
                blocks.add(MDBlock.Code(codeContent.toString().trimEnd()))
                i++
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                blocks.add(MDBlock.Heading(level, line.substring(level).trim()))
                i++
            }
            line.startsWith("---") -> {
                blocks.add(MDBlock.Rule)
                i++
            }
            line.startsWith("> ") -> {
                val quoteContent = java.lang.StringBuilder()
                while (i < lines.size && lines[i].startsWith("> ")) {
                    quoteContent.appendLine(lines[i].substring(2))
                    i++
                }
                blocks.add(MDBlock.Quote(quoteContent.toString().trimEnd()))
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                blocks.add(MDBlock.ListItem(line.substring(2)))
                i++
            }
            else -> {
                val paragraph = java.lang.StringBuilder()
                while (i < lines.size && lines[i].isNotBlank() && !blockStartRegex.containsMatchIn(lines[i])) {
                    // 核心修复：使用 \n 替代空格，完美保留排版换行！
                    paragraph.appendLine(lines[i])
                    i++
                }
                blocks.add(MDBlock.Paragraph(paragraph.toString().trimEnd()))
            }
        }
    }
    return blocks
}

// 行内样式解析器（处理加粗和行内代码）
@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    return buildAnnotatedString {
        val regex = Regex("\\*\\*(.*?)\\*\\*|`(.*?)`")
        var lastIndex = 0
        
        regex.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            
            if (matchResult.groups[1] != null) {
                // 加粗
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matchResult.groupValues[1])
                }
            } else if (matchResult.groups[2] != null) {
                // 行内代码
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeColor,
                        color = codeTextColor
                    )
                ) {
                    append(" ${matchResult.groupValues[2]} ")
                }
            }
            lastIndex = matchResult.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}