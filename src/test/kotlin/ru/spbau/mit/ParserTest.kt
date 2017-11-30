package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.tree.TerminalNode
import org.junit.Test
import ru.spbau.mit.parser.SimpleLexer
import ru.spbau.mit.parser.SimpleParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun testLiteral() {
        val literal = "239"
        val expr = getExpression(literal)
        expr.checkLiteral(literal)
    }

    @Test
    fun testBinOp() {
        val left = "2"
        val right = "7"

        getExpression("$left * $right").checkMult(left, right, SimpleParser.MultiplicationExpressionContext::ASTERISK)
        getExpression("$left / $right").checkMult(left, right, SimpleParser.MultiplicationExpressionContext::DIVISION)
        getExpression("$left % $right").checkMult(left, right, SimpleParser.MultiplicationExpressionContext::MODULUS)
    }

    @Test
    fun testCompare() {
        val left = "2"
        val right = "7"

        getExpression("$left == $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::EQ)
        getExpression("$left != $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::NEQ)
        getExpression("$left > $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::GR)
        getExpression("$left >= $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::GEQ)
        getExpression("$left < $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::LS)
        getExpression("$left <= $right").checkComparison(left, right, SimpleParser.CompareExpressionContext::LEQ)
    }

    @Test
    fun testLogic() {
        val left = "7 == 16"
        val right = "2 != 39"
        val checkLeft: SimpleParser.ExpressionContext.() -> Unit = { checkComparison("7", "16", SimpleParser.CompareExpressionContext::EQ) }
        val checkRight: SimpleParser.ExpressionContext.() -> Unit = { checkComparison("2", "39", SimpleParser.CompareExpressionContext::NEQ) }

        getExpression("$left || $right").checkLogic(checkLeft, checkRight, SimpleParser.LogicExpressionContext::OR)
        getExpression("$left && $right").checkLogic(checkLeft, checkRight, SimpleParser.LogicExpressionContext::AND)
    }

    @Test
    fun testFunctionCall() {
        val name1 = "foo"
        val name2 = "println"
        val args0 = listOf<String>()
        val args1 = listOf("6")
        val args3 = listOf("6", "0", "2")

        fun assemble(name: String, args: List<String>) = "$name(${args.joinToString(", ")})"

        getExpression(assemble(name1, args0)).checkFunctionCall(name1, args0)
        getExpression(assemble(name2, args1)).checkFunctionCall(name2, args1)
        getExpression(assemble(name2, args3)).checkFunctionCall(name2, args3)
    }

    @Test
    fun testVariable() {
        val name = "x"
        val value = "5"

        val decl = getStatement("var $name = $value").variableDeclaration()
        assertNotNull(decl)

        assertEquals(name, decl!!.ID().text)
        decl.expression().checkLiteral(value)
    }

    @Test
    fun testFunction() {
        fun doTestFunction(name: String, body: String, params: List<String>) {
            val decl = getStatement("fun $name(${params.joinToString(", ")}) { $body }").functionDeclaration()
            assertNotNull(decl)

            assertEquals(name, decl!!.ID().text)
            decl.parameterNames().ID().zip(params).forEach { (id, param) -> assertEquals(param, id.text) }

            val innerBlock = decl.blockWithBraces().block()
            innerBlock.checkStatementCount(1)
            innerBlock.statement(0).expression().checkLiteral(body)
        }

        doTestFunction("foo", "4", listOf())
        doTestFunction("boo", "4", listOf("hey"))
        doTestFunction("foo", "4", listOf("pom", "bom"))
    }

    @Test
    fun testReturn() {
        val value = "5"
        val ret = getStatement("return $value").returnStatement()
        assertNotNull(ret)
        ret!!.expression().checkLiteral(value)
    }

    @Test
    fun testIf() {
        val value = "5"
        val ife = getStatement("if ($value) { $value } else { $value }").ifStatement()
        val iff = getStatement("if ($value) { $value }").ifStatement()
        assertNotNull(ife)
        assertNotNull(iff)
        assertNotNull(ife.expression())
        assertNotNull(iff.expression())
        ife.expression().checkLiteral(value)
        iff.expression().checkLiteral(value)
        assertEquals(2, ife.blockWithBraces().size)
        assertEquals(1, iff.blockWithBraces().size)
    }

    @Test
    fun testWhile() {
        val value = "5"
        val wh = getStatement("while ($value) { $value }").whileStatement()
        assertNotNull(wh)
        assertNotNull(wh.expression())
        wh.expression().checkLiteral(value)
    }

    private fun SimpleParser.ExpressionContext.checkLiteral(expectedValue: String) {
        assertTrue(this is SimpleParser.LiteralExpressionContext)

        val literalExpr = this as SimpleParser.LiteralExpressionContext
        assertEquals(expectedValue, literalExpr.LITERAL().text)
    }

    private  fun getBlock(program: String): SimpleParser.BlockContext {
        val lexer = SimpleLexer(CharStreams.fromString(program))
        val parser = SimpleParser(BufferedTokenStream(lexer))
        return parser.file().block()
    }

    private  fun getStatement(program: String): SimpleParser.StatementContext {
        val block = getBlock(program)
        block.checkStatementCount(1)
        return block.statement(0)
    }

    private  fun getExpression(program: String): SimpleParser.ExpressionContext {
        val expr = getStatement(program).expression()
        assertNotNull(expr)
        return expr!!
    }

    private fun SimpleParser.BlockContext.checkStatementCount(expectedSize: Int) = assertEquals(expectedSize, statement().size)

    private fun SimpleParser.ExpressionContext.checkMult(
            left: String,
            right: String,
            op: SimpleParser.MultiplicationExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is SimpleParser.MultiplicationExpressionContext)

        val mult = this as SimpleParser.MultiplicationExpressionContext
        assertEquals(2, mult.expression().size)
        mult.expression(0).checkLiteral(left)
        mult.expression(1).checkLiteral(right)
        assertNotNull(mult.op())
    }

    private fun SimpleParser.ExpressionContext.checkComparison(
            left: String,
            right: String,
            op: SimpleParser.CompareExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is SimpleParser.CompareExpressionContext)

        val comparison = this as SimpleParser.CompareExpressionContext
        assertEquals(2, comparison.expression().size)
        comparison.expression(0).checkLiteral(left)
        comparison.expression(1).checkLiteral(right)
        assertNotNull(comparison.op())
    }

    private fun SimpleParser.ExpressionContext.checkLogic(
            checkLeft: SimpleParser.ExpressionContext.() -> Unit,
            checkRight: SimpleParser.ExpressionContext.() -> Unit,
            op: SimpleParser.LogicExpressionContext.() -> TerminalNode
    ) {
        assertTrue(this is SimpleParser.LogicExpressionContext)

        val logic = this as SimpleParser.LogicExpressionContext
        assertEquals(2, logic.expression().size)
        logic.expression(0).checkLeft()
        logic.expression(1).checkRight()
        assertNotNull(logic.op())
    }

    private fun SimpleParser.ExpressionContext.checkFunctionCall(
            name: String,
            args: List<String>
    ) {
        assertTrue(this is SimpleParser.FunctionCallExpressionContext)

        val funcExpr = (this as SimpleParser.FunctionCallExpressionContext).functionCall()
        assertEquals(name, funcExpr.ID().text)
        assertEquals(args.size, funcExpr.arguments().expression().size)
        for (i in 0 until args.size) {
            funcExpr.arguments().expression(i).checkLiteral(args[i])
        }
    }
}