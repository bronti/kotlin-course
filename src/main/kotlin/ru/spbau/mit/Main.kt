package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import ru.spbau.mit.parser.SimpleLexer
import ru.spbau.mit.parser.SimpleParser

fun main(args: Array<String>) {
    val tokenStream = BufferedTokenStream(SimpleLexer(CharStreams.fromString("var x = 5 * 8\n x")))
    val parser = SimpleParser(tokenStream)
    val tree = parser.file()
    println("Result: ${EvaluateVisitor().visit(tree)}")

//    println(tree)
}
