package ru.spbau.mit

import org.antlr.v4.runtime.BufferedTokenStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.misc.ParseCancellationException
import ru.spbau.mit.parser.SimpleLexer
import ru.spbau.mit.parser.SimpleParser
import java.io.IOException
import java.io.OutputStreamWriter

fun main(args: Array<String>) {
    if (args.isEmpty() || args.size > 1) {
        System.err.println("Path to file is required.")
        return
    }

    try {
        val lexer = SimpleLexer(CharStreams.fromFileName(args.first()))
        val parser = SimpleParser(BufferedTokenStream(lexer))
        val tree = parser.file()
        val evaluator = EvaluateVisitor(OutputStreamWriter(System.out))
        val result = evaluator.visit(tree)
        when (result) {
            is EvaluateVisitor.Result.Returned -> println("Program finished with code: ${result.value}")
            else -> throw IllegalStateException()
        }
    } catch (e: EvaluateVisitor.EvaluationException) {
        System.err.println("Evaluation error on line ${e.lineNumber}: ${e.message}.")
    } catch (e: IOException) {
        System.err.println("IO exception: ${e.message}")
    } catch (e: ParseCancellationException) {
        System.err.println("Parsing exception: ${e.message}")
    }
}
