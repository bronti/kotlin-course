package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import org.junit.Test
import ru.spbau.mit.parser.SimpleLexer
import ru.spbau.mit.parser.SimpleParser
import java.io.StringWriter
import kotlin.test.assertEquals
import ru.spbau.mit.EvaluateVisitor.Result.*

class EvaluationTest {
    //todo: test mistakes

    @Test
    fun testLiteral() {
        doTestIntExpression("239", 239)
        doTestIntExpression("0", 0)
    }

    @Test
    fun testBinOp() {
        doTestIntExpression("240", 240)
        doTestIntExpression("2 * 2", 4)
        doTestIntExpression("7 / 2", 3)
        doTestIntExpression("5 % 1", 0)
    }

    @Test
    fun testCompare() {
        doTestBoolExpression("7 == 15", false)
        doTestBoolExpression("7 != 15", true)
        doTestBoolExpression("7 > 15", false)
        doTestBoolExpression("7 >= 15", false)
        doTestBoolExpression("7 <= 15", true)
        doTestBoolExpression("7 < 15", true)
    }

    @Test
    fun testLogic() {
        doTestBoolExpression("(7 == 15) || (0 == 0)", true)
        doTestBoolExpression("(7 == 15) || (0 == 1)", false)
        doTestBoolExpression("(7 == 15) && (0 == 0)", false)
        doTestBoolExpression("(7 == 7) && (0 == 0)", true)
    }

    @Test
    fun testPrintln() {
        doTest("println()", "\n")
        doTest("println(0)", "0\n")
        doTest("println(0, 12)", "0 12\n")
        doTest("println(0)\nprintln(12)", "0\n12\n")
    }

    @Test
    fun testVariable() {
        doTest("var x = 5\nprintln(x)", "5\n")
        doTest("var x = 5\nx = 4\nprintln(x)", "4\n")
    }

    @Test
    fun testVariableInnerScope() {
        doTest("var x = 5\nif (0 == 0) { var x = 2\nprintln(x) }\nprintln(x)", "2\n5\n")
    }

    @Test
    fun testFunction() {
        doTest("fun f() { println(5) }\nf()", "5\n")
    }

    @Test
    fun testFunctionParams() {
        doTest("fun f(a) { println(a) }\nf(2)", "2\n")
        doTest("fun f(a, b) { println(a)\nprintln(b) }\nf(2, 4)", "2\n4\n")
    }

    @Test
    fun testReturn() {
        doTest("fun f() { return 5 }\nprintln(f())", "5\n")
        doTest("fun f() { }\nprintln(f())", "0\n")
        doTest("return 6", "", Returned(6))
        doTest("", "", Returned(0))

        doTest("var x = 0\n while(x < 3) {println(x)\nx = x + 1\nif(x==2) { return 0 }}", "0\n1\n")
    }

    @Test
    fun testIf() {
        doTest("if (0 == 0) { println(2) } else { println(3) }", "2\n")
        doTest("if (0 != 0) { println(2) } else { println(3) }", "3\n")
        doTest("if (0 == 0) { println(2) }", "2\n")
        doTest("if (0 != 0) { println(2) }", "")
    }

    @Test
    fun testWhile() {
        doTest("var x = 0\n while(x < 2) {println(x)\nx = x + 1}", "0\n1\n")
        doTest("var x = 1\n while(x < 2) {println(x)\nx = x + 1}", "1\n")
        doTest("var x = 2\n while(x < 2) {println(x)\nx = x + 1}", "")
    }

    @Test
    fun testFunctionInnerScope() {
        doTest("fun f() { println(5) }\nif (0 == 0) { fun f() { println(2) }\nf()}\nf()", "2\n5\n")
    }

    private  fun doTestIntExpression(expr: String, expected: Int) {
        doTest("println($expr)", "$expected\n")
    }

    private  fun doTestBoolExpression(expr: String, expected: Boolean) {
        doTest("if ($expr) { println(1) } else { println(0) } ", if (expected) "1\n" else "0\n")
    }

    private  fun doTest(program: String, expectedPrinted: String, expectedResult: EvaluateVisitor.Result = Returned(0)) {
        val output = StringWriter()
        val lexer = SimpleLexer(CharStreams.fromString(program))
        val parser = SimpleParser(BufferedTokenStream(lexer))
        val tree = parser.file()
        val evaluator = EvaluateVisitor(output)

        val result = evaluator.visit(tree)
        val printed = output.toString()

        assertEquals(expectedPrinted, printed)
        assertEquals(expectedResult, result)
    }
}