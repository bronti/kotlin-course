package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import ru.spbau.mit.parser.SimpleLexer

fun main(args: Array<String>) {
    val tokenStream = BufferedTokenStream(SimpleLexer(CharStreams.fromString("whie while while67 + 783")))
    tokenStream.fill()

    println(tokenStream.tokens)
}
