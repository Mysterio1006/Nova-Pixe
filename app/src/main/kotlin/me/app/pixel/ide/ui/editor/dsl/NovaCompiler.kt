package me.app.pixel.ide.ui.editor.dsl

class NovaCompileException(message: String, val line: Int) : RuntimeException("Line $line: $message")

class NovaLexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private val indentStack = mutableListOf(0)

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        while (indentStack.size > 1) {
            indentStack.removeLast()
            addToken(TokenType.DEDENT)
        }
        tokens.add(Token(TokenType.EOF, "", line))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '\n' -> { line++; handleIndentation() }
            ':' -> addToken(TokenType.COLON)
            ',' -> addToken(TokenType.COMMA)
            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            '[' -> addToken(TokenType.LBRACKET)
            ']' -> addToken(TokenType.RBRACKET)
            '+' -> addToken(TokenType.PLUS)
            '-' -> if (match('>')) addToken(TokenType.ARROW) else addToken(TokenType.MINUS) // 解析 ->
            '*' -> addToken(TokenType.STAR)
            '%' -> addToken(TokenType.PERCENT)
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance() 
                } else {
                    addToken(TokenType.SLASH)
                }
            }
            '=' -> if (match('=')) addToken(TokenType.EQ) else addToken(TokenType.ASSIGN)
            '!' -> if (match('=')) addToken(TokenType.NEQ) else throw NovaCompileException("未知的语法字符: '!'", line)
            '<' -> if (match('=')) addToken(TokenType.LTE) else addToken(TokenType.LT)
            '>' -> if (match('=')) addToken(TokenType.GTE) else addToken(TokenType.GT)
            '#' -> hexColor()
            '"', '\'' -> string(c)
            ' ', '\r', '\t' -> {}
            else -> {
                if (c.isDigit()) {
                    number()
                } else if (c.isLetter() || c == '_') {
                    identifier()
                } else {
                    throw NovaCompileException("未知的语法字符: '$c'", line)
                }
            }
        }
    }

    private fun handleIndentation() {
        if (tokens.isNotEmpty() && tokens.last().type != TokenType.NEWLINE && tokens.last().type != TokenType.COLON && tokens.last().type != TokenType.ARROW) {
            addToken(TokenType.NEWLINE)
        }

        var spaces = 0
        while (!isAtEnd()) {
            val p = peek()
            if (p == ' ') { spaces++; advance() }
            else if (p == '\t') { spaces += 4; advance() }
            else if (p == '\r') { advance() }
            else if (p == '\n') {
                spaces = 0; line++; advance() 
            } else break
        }

        if (isAtEnd()) return

        val currentIndent = indentStack.last()
        if (spaces > currentIndent) {
            indentStack.add(spaces)
            addToken(TokenType.INDENT)
        } else if (spaces < currentIndent) {
            while (indentStack.size > 1 && indentStack.last() > spaces) {
                indentStack.removeLast()
                addToken(TokenType.DEDENT)
            }
            if (indentStack.last() != spaces) {
                throw NovaCompileException("缩进级别无法匹配 (IndentationError)", line)
            }
        }
    }

    private fun identifier() {
        while (peek().isLetterOrDigit() || peek() == '_') advance()
        val text = source.substring(start, current)
        val type = when (text) {
            "repeat" -> TokenType.REPEAT
            "if" -> TokenType.IF
            "else" -> TokenType.ELSE
            "macro" -> TokenType.MACRO
            "mirrorX" -> TokenType.MIRROR_X
            "mirrorY" -> TokenType.MIRROR_Y
            "offset" -> TokenType.OFFSET
            "as" -> TokenType.AS
            else -> TokenType.IDENTIFIER
        }
        addToken(type)
    }

    private fun number() {
        while (peek().isDigit()) advance()
        addToken(TokenType.NUMBER)
    }

    private fun hexColor() {
        while (peek().isLetterOrDigit()) advance()
        addToken(TokenType.HEX_COLOR)
    }
    
    private fun string(quote: Char) {
        while (peek() != quote && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
        if (isAtEnd()) throw NovaCompileException("字符串未闭合", line)
        advance()
        addToken(TokenType.STRING)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        current++; return true
    }
    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun advance(): Char = source[current++]
    private fun isAtEnd(): Boolean = current >= source.length
    
    private fun addToken(type: TokenType) {
        val text = source.substring(start, current)
        val lexeme = if (type == TokenType.STRING) text.substring(1, text.length - 1) else text
        tokens.add(Token(type, lexeme, line))
    }
}

class NovaParser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            if (match(TokenType.NEWLINE)) continue 
            val stmt = statement()
            if (stmt != null) statements.add(stmt)
        }
        return statements
    }

    private fun statement(): Stmt? {
        while (match(TokenType.NEWLINE)) { }
        if (isAtEnd() || check(TokenType.DEDENT)) return null
        
        if (match(TokenType.REPEAT)) return repeatStatement()
        if (match(TokenType.OFFSET)) return offsetStatement()
        if (match(TokenType.IF)) return ifStatement()
        if (match(TokenType.MACRO)) return macroDef()
        if (match(TokenType.MIRROR_X)) return mirrorStatement(isX = true)
        if (match(TokenType.MIRROR_Y)) return mirrorStatement(isX = false)
        
        return assignOrCall()
    }

    private fun repeatStatement(): Stmt {
        val times = expression() 
        var varName: String? = null
        if (match(TokenType.AS)) {
            varName = consume(TokenType.IDENTIFIER, "预期循环变量别名").lexeme
        }
        consume(TokenType.COLON, "repeat 语句后必须以冒号 ':' 结尾")
        match(TokenType.NEWLINE) 
        val body = block()
        return Stmt.Repeat(times, varName, Stmt.Block(body))
    }

    private fun offsetStatement(): Stmt {
        val dx = expression()
        consume(TokenType.COMMA, "offset 需要 x 和 y 两个参数，请用逗号分隔")
        val dy = expression()
        consume(TokenType.COLON, "offset 语句后必须以冒号 ':' 结尾")
        match(TokenType.NEWLINE)
        val body = block()
        return Stmt.Offset(dx, dy, Stmt.Block(body))
    }

    private fun ifStatement(): Stmt {
        val condition = expression()
        consume(TokenType.COLON, "if 语句后必须以冒号 ':' 结尾")
        match(TokenType.NEWLINE)
        val thenBranch = block()
        
        var elseBranch: List<Stmt>? = null
        var tempCurrent = current
        while (tempCurrent < tokens.size && tokens[tempCurrent].type == TokenType.NEWLINE) tempCurrent++
        
        if (tempCurrent < tokens.size && tokens[tempCurrent].type == TokenType.ELSE) {
            current = tempCurrent
            advance()
            consume(TokenType.COLON, "else 语句后必须以冒号 ':' 结尾")
            match(TokenType.NEWLINE)
            elseBranch = block()
        }
        
        return Stmt.If(condition, Stmt.Block(thenBranch), elseBranch?.let { Stmt.Block(it) })
    }

    private fun macroDef(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "预期宏的名称").lexeme
        consume(TokenType.LPAREN, "宏定义缺少 '('")
        val params = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENTIFIER, "预期参数名称").lexeme)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "宏定义缺少 ')'")
        
        // 升级：区分计算宏和动作宏
        if (match(TokenType.ARROW)) {
            val expr = expression()
            match(TokenType.NEWLINE)
            return Stmt.MacroDef(name, params, null, expr)
        } else {
            consume(TokenType.COLON, "宏定义后必须以 ':' 或 '->' 结尾")
            match(TokenType.NEWLINE)
            val body = block()
            return Stmt.MacroDef(name, params, Stmt.Block(body), null)
        }
    }

    private fun mirrorStatement(isX: Boolean): Stmt {
        val axis = expression()
        consume(TokenType.COLON, "mirror 语句后必须以冒号 ':' 结尾")
        match(TokenType.NEWLINE)
        val body = block()
        return Stmt.Mirror(isX, axis, Stmt.Block(body))
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        consume(TokenType.INDENT, "预期此处应该有一个缩进块 (IndentationError)")
        
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            val stmt = statement()
            if (stmt != null) statements.add(stmt)
        }
        
        consume(TokenType.DEDENT, "缩进级别异常退出")
        return statements
    }

    private fun assignOrCall(): Stmt {
        val nameToken = consume(TokenType.IDENTIFIER, "预期变量名或调用名")
        
        if (match(TokenType.ASSIGN)) {
            val value = expression()
            match(TokenType.NEWLINE) 
            return Stmt.Assign(nameToken.lexeme, value)
        } else if (match(TokenType.LPAREN)) {
            val args = mutableListOf<Expr>()
            if (!check(TokenType.RPAREN)) {
                do { args.add(expression()) } while (match(TokenType.COMMA))
            }
            consume(TokenType.RPAREN, "调用缺少右括号 ')'")
            match(TokenType.NEWLINE) 
            return Stmt.FuncCall(nameToken.lexeme, args)
        }
        throw NovaCompileException("无效的语句，预期 '=' (赋值) 或 '(' (调用)", nameToken.line)
    }

    private fun expression(): Expr = equality()

    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = previous()
            expr = Expr.Binary(expr, op, comparison())
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()
        while (match(TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)) {
            val op = previous()
            expr = Expr.Binary(expr, op, term())
        }
        return expr
    }
    
    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous()
            expr = Expr.Binary(expr, op, factor())
        }
        return expr
    }
    
    private fun factor(): Expr {
        var expr = unary() 
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous()
            expr = Expr.Binary(expr, op, unary())
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.MINUS)) {
            val op = previous()
            val right = unary()
            return Expr.Unary(op, right)
        }
        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(TokenType.LBRACKET)) {
                val index = expression()
                consume(TokenType.RBRACKET, "预期 ']' 闭合列表索引")
                expr = Expr.IndexAccess(expr, index)
            } else if (match(TokenType.LPAREN)) {
                if (expr !is Expr.Variable) throw NovaCompileException("非法的内置函数调用", previous().line)
                val name = expr.name
                val args = mutableListOf<Expr>()
                if (!check(TokenType.RPAREN)) {
                    do { args.add(expression()) } while (match(TokenType.COMMA))
                }
                consume(TokenType.RPAREN, "预期 ')' 闭合函数参数")
                expr = Expr.FuncCall(name, args)
            } else {
                break
            }
        }
        return expr
    }
    
    private fun primary(): Expr {
        if (match(TokenType.NUMBER)) return Expr.NumLiteral(previous().lexeme.toInt())
        if (match(TokenType.HEX_COLOR)) return Expr.ColorLiteral(previous().lexeme)
        if (match(TokenType.STRING)) return Expr.StrLiteral(previous().lexeme) 
        if (match(TokenType.IDENTIFIER)) return Expr.Variable(previous().lexeme)
        
        if (match(TokenType.LBRACKET)) {
            val elements = mutableListOf<Expr>()
            if (!check(TokenType.RBRACKET)) {
                do {
                    elements.add(expression())
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.RBRACKET, "预期 ']' 闭合列表声明")
            return Expr.ListLiteral(elements)
        }
        
        if (match(TokenType.LPAREN)) {
            val expr = expression()
            consume(TokenType.RPAREN, "括号未闭合，预期 ')'")
            return expr
        }
        throw NovaCompileException("预期的表达式或非法字符", peek().line)
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) if (check(type)) { advance(); return true }
        return false
    }
    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw NovaCompileException(message, peek().line)
    }
    private fun check(type: TokenType): Boolean = if (isAtEnd()) false else peek().type == type
    private fun advance(): Token { if (!isAtEnd()) current++; return previous() }
    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
    private fun peek(): Token = tokens[current]
    private fun previous(): Token = tokens[current - 1]
}