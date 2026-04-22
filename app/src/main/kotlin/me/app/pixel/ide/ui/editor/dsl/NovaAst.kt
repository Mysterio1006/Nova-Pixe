// app/src/main/kotlin/me/app/pixel/ide/ui/editor/dsl/NovaAst.kt
package me.app.pixel.ide.ui.editor.dsl

// Pythonic 词法 Token 定义
enum class TokenType {
    IDENTIFIER, NUMBER, HEX_COLOR, STRING,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, GT, LTE, GTE,
    ASSIGN, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, COLON,
    REPEAT, IF, ELSE, MACRO, MIRROR_X, MIRROR_Y,
    OFFSET, AS, ARROW, 
    NEWLINE, INDENT, DEDENT, EOF
}

data class Token(val type: TokenType, val lexeme: String, val line: Int)

// 抽象语法树节点

sealed class Expr {
    data class NumLiteral(val value: Int) : Expr()
    data class ColorLiteral(val hex: String) : Expr()
    data class StrLiteral(val value: String) : Expr()
    data class Variable(val name: String) : Expr()
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    
    data class ListLiteral(val elements: List<Expr>) : Expr()
    data class IndexAccess(val list: Expr, val index: Expr) : Expr()
    data class FuncCall(val name: String, val args: List<Expr>) : Expr()
}

sealed class Stmt {
    data class Assign(val name: String, val value: Expr) : Stmt()
    data class FuncCall(val name: String, val args: List<Expr>) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    data class Repeat(val times: Expr, val varName: String?, val body: Block) : Stmt()
    data class If(val condition: Expr, val thenBranch: Block, val elseBranch: Block?) : Stmt()
    data class MacroDef(val name: String, val params: List<String>, val body: Block?, val returnExpr: Expr?) : Stmt()
    data class Mirror(val isX: Boolean, val axis: Expr, val body: Block) : Stmt()
    data class Offset(val dx: Expr, val dy: Expr, val body: Block) : Stmt()
}