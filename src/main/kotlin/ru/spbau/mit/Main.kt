package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import ru.spbau.mit.parser.SimpleLexer
import ru.spbau.mit.parser.SimpleParser

fun main(args: Array<String>) {
    val tokenStream = BufferedTokenStream(SimpleLexer(CharStreams.fromString(
            """
                var x = 0
                while (x < 3) {
                    x = x + 1
                    if (x == 2) {
                        return 10
                    }
                }
                return 5
            """.trimIndent()
    )))
    val parser = SimpleParser(tokenStream)
    val tree = parser.file()
    println("Result: ${EvaluateVisitor().visit(tree)}")

//    println(tree)
}
