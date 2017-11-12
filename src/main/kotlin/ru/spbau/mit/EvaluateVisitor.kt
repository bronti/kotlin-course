package ru.spbau.mit

import com.sun.org.apache.regexp.internal.RE
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import ru.spbau.mit.parser.SimpleParser
import ru.spbau.mit.parser.SimpleParserBaseVisitor

class EvaluateVisitor : SimpleParserBaseVisitor<EvaluateVisitor.Result>() {

    private var context: Context = Context()

    sealed class Result {
        object None: Result()
        data class Error(val lineNumber: Int, val msg: String): Result()
        data class Evaled<out T>(val value: T): Result()
        data class Returned<out T>(val value: T): Result()

        val isLast get() = this is Error || this is Returned<*>
    }

    override fun defaultResult(): Result = Result.None

    override fun shouldVisitNextChild(node: RuleNode, currentResult: Result) = !currentResult.isLast

    override fun visitFile(ctx: SimpleParser.FileContext) = ctx.block().accept(this)!!

    override fun visitBlock(ctx: SimpleParser.BlockContext): Result {
        return ctx.children.fold(defaultResult()) { prevResult, child ->
            if (!shouldVisitNextChild(ctx, prevResult)) return prevResult
            child.accept(this)
        }
    }

    override fun visitBlockWithBraces(ctx: SimpleParser.BlockWithBracesContext): Result {
        context.goDeeper()
        return ctx.block().accept(this)
    }

    override fun visitVariableDeclaration(ctx: SimpleParser.VariableDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        if (!context.scope.defineVariable(name)) return Result.Error(ctx.VAR().symbol.line, "Redefinition of variable $name.")
        val exprResult = ctx.expression() ?: return Result.None
        return handleExpression(
                exprResult,
                { context.scope.setVariable(name, it); Result.None },
                { context.scope.setVariable(name, it); Result.None }
        )
    }

    override fun visitReturnStatement(ctx: SimpleParser.ReturnStatementContext)
            = handleExpression(ctx.expression(), { Result.Returned(it) }, { Result.Returned(it) })

    override fun visitVariableExpression(ctx: SimpleParser.VariableExpressionContext): Result {
        val name = ctx.ID().symbol.text
        val varType = context.scope.getVariableType(name)
        return when (varType) {
            Scope.VariableType.NONE -> Result.Error(ctx.ID().symbol.line, "Undefined or unset variable $name.")
            Scope.VariableType.BOOL -> Result.Evaled(context.scope.getBoolVariable(name)!!)
            Scope.VariableType.INT -> Result.Evaled(context.scope.getIntVariable(name)!!)
        }
    }

    override fun visitMultiplicationExpression(ctx: SimpleParser.MultiplicationExpressionContext): Result {
        return visitBinaryIntOp(ctx.expression()) { left, right ->
            val result = when {
                ctx.ASTERISK() != null -> left * right
                ctx.DIVISION() != null -> left / right
                ctx.MODULUS() != null -> left % right
                else -> throw IllegalStateException()
            }
            Result.Evaled(result)
        }
    }

    override fun visitLiteralExpression(ctx: SimpleParser.LiteralExpressionContext): Result {
        return ctx.LITERAL().symbol.text.toIntOrNull()?.let { Result.Evaled(it) }
                ?: Result.Error(ctx.LITERAL().symbol.line, "Cannot read literal ${ctx.LITERAL().symbol.text}")
    }

    override fun visitSummExpression(ctx: SimpleParser.SummExpressionContext): Result {
        return visitBinaryIntOp(ctx.expression()) { left, right ->
            val result = when {
                ctx.PLUS() != null -> left + right
                ctx.MINUS() != null -> left - right
                else -> throw IllegalStateException()
            }
            Result.Evaled(result)
        }
    }

    private fun visitBinaryIntOp(exprs: List<SimpleParser.ExpressionContext>, op: (Int, Int) -> Result): Result {
        val (left, right) = exprs.map { it.accept(this).let { res ->
                    if (res is Result.Error) { return res }
                    else (res as Result.Evaled<*>).value as Int
                }
        }
        return op(left, right)
    }

    private fun handleExpression(expr: SimpleParser.ExpressionContext, handleInt: (Int) -> Result, handleBool: (Boolean) -> Result): Result {
        val result = expr.accept(this)
        return when (result) {
            is Result.Error -> return result
            is Result.Evaled<*> -> {
                when (result.value) {
                    is Int -> handleInt(result.value)
                    is Boolean -> handleBool(result.value)
                    else -> throw IllegalArgumentException()
                }
            }
            else -> throw IllegalStateException()
        }
    }

}