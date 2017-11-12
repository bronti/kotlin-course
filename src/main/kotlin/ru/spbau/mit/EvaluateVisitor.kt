package ru.spbau.mit

import org.antlr.v4.runtime.tree.RuleNode
import ru.spbau.mit.parser.SimpleParser
import ru.spbau.mit.parser.SimpleParserBaseVisitor

class EvaluateVisitor : SimpleParserBaseVisitor<EvaluateVisitor.Result>() {

    private var context: Context = Context()

    sealed class Result {
        object None: Result()
        data class Error(val lineNumber: Int, val msg: String): Result()
        data class Evaled<out T>(val value: T): Result()
        data class Returned(val value: Int): Result()

        val isLast get() = this is Error || this is Returned
    }

    override fun defaultResult(): Result = Result.None

    override fun shouldVisitNextChild(node: RuleNode, currentResult: Result) = !currentResult.isLast

    override fun visitFile(ctx: SimpleParser.FileContext): Result {
        val result = ctx.block().accept(this)
        return if (result.isLast) result
        else Result.Returned(0)
    }

    override fun visitBlock(ctx: SimpleParser.BlockContext): Result {
        return ctx.children.fold(defaultResult()) { prevResult, child ->
            if (prevResult.isLast) return prevResult
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
                { Result.Error(ctx.VAR().symbol.line, "Only Integer variables are allowed.") }
        )
    }

    override fun visitReturnStatement(ctx: SimpleParser.ReturnStatementContext)
            = handleExpression(ctx.expression(), { Result.Returned(it) }, { Result.Error(ctx.RETURN().symbol.line, "Only Integer can be returned.") })

    override fun visitVariableExpression(ctx: SimpleParser.VariableExpressionContext): Result {
        val name = ctx.ID().symbol.text
        return context.scope.getVariable(name)?.let { Result.Evaled(it) }
                ?: Result.Error(ctx.ID().symbol.line, "Undefined or unset variable $name.")
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

    override fun visitWhileStatement(ctx: SimpleParser.WhileStatementContext): Result {
        while (true) {
            val cond = handleCondition(ctx.expression(), ctx.WHILE().symbol.line)
            if (cond is Result.Error) return cond
            if (cond !is Result.Evaled<*> || cond.value !is Boolean) throw IllegalStateException()
            if (!cond.value) break

            val body = ctx.blockWithBraces().accept(this)
            if (body.isLast) return body
        }
        return Result.None
    }

    override fun visitIfStatement(ctx: SimpleParser.IfStatementContext): Result {
        val cond = handleCondition(ctx.expression(), ctx.IF().symbol.line)
        if (cond is Result.Error) return cond
        if (cond !is Result.Evaled<*> || cond.value !is Boolean) throw IllegalStateException()
        if (cond.value) {
            val body = ctx.blockWithBraces(0).accept(this)
            if (body.isLast) return body
        } else if (ctx.ELSE() != null) {
            val elseBody = ctx.blockWithBraces(1).accept(this)
            if (elseBody.isLast) return elseBody
        }
        return Result.None
    }

    override fun visitAssignment(ctx: SimpleParser.AssignmentContext): Result {
        val name = ctx.ID().symbol.text
        if (!context.scope.isDefinedVariable(name)) {
            return Result.Error(ctx.ID().symbol.line, "There is no variable with name $name.")
        }
        val exprResult = ctx.expression()
        return handleExpression(
                exprResult,
                { context.scope.setVariable(name, it); Result.None },
                { Result.Error(ctx.ID().symbol.line, "Cannot assign Bool value to variable.") }
        )
    }

    override fun visitFunctionDeclaration(ctx: SimpleParser.FunctionDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val parameters = ctx.parameterNames().ID().map { it.symbol.text }
        if (parameters.distinct().size != parameters.size)
            return Result.Error(ctx.ID().symbol.line, "Same param names in function is not allowed.")
        val body = ctx.blockWithBraces()
        return if (context.scope.defineFunction(name, parameters, body)) Result.None
        else Result.Error(ctx.ID().symbol.line, "Redefenition of the function $name.")
    }

    override fun visitFunctionCall(ctx: SimpleParser.FunctionCallContext): Result {
        val name = ctx.ID().symbol.text
        val arguments = ctx.arguments().expression().map {
            val result = it.accept(this)
            if (result is Error) return result
            if (result is Result.Evaled<*> && result.value is Boolean) {
                return Result.Error(it.start.line, "Cannot pass Boolen as function argument.")
            }
            if (result !is Result.Evaled<*>) throw IllegalStateException()
            result.value as Int
        }

        val (argNames, body) = context.scope.getFunction(name) ?:
                return if (context.scope.callPredefinedFunction(name, arguments)) Result.None
                else Result.Error(ctx.start.line, "There is no such function")

        if (argNames.size != arguments.size) return Result.Error(ctx.start.line, "Invalid number of arguments.")

        context.goDeeper()
        for ((argName, value) in argNames.zip(arguments)) {
            context.scope.defineVariable(argName)
            context.scope.setVariable(argName, value)
        }
        val result = body.accept(this)
        context.closeCurrentScope()
        return result
    }

    override fun visitFunctionCallExpression(ctx: SimpleParser.FunctionCallExpressionContext): Result {
        return ctx.functionCall().accept(this)
    }

    override fun visitCompareExpression(ctx: SimpleParser.CompareExpressionContext): Result {
        val (left, right) = ctx.expression().map { it.accept(this).let { res ->
            if (res is Result.Error) { return res }
            else (res as Result.Evaled<*>).value as Int
        }}
        return Result.Evaled(when {
            ctx.EQ() != null -> left == right
            ctx.NEQ() != null -> left != right
            ctx.GEQ() != null -> left >= right
            ctx.GR() != null -> left > right
            ctx.LEQ() != null -> left <= right
            ctx.LS() != null -> left < right
            else -> throw IllegalStateException()
        })
    }

    override fun visitParenthesesExpression(ctx: SimpleParser.ParenthesesExpressionContext): Result {
        return ctx.expression().accept(this)
    }

    override fun visitLogicExpression(ctx: SimpleParser.LogicExpressionContext): Result {
        val (left, right) = ctx.expression().map { it.accept(this).let { res ->
            if (res is Result.Error) { return res }
            else (res as Result.Evaled<*>).value as Boolean
        }}
        return Result.Evaled(when {
            ctx.OR() != null -> left || right
            ctx.AND() != null -> left && right
            else -> throw IllegalStateException()
        })
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

    private fun handleCondition(expr: SimpleParser.ExpressionContext, line: Int): Result {
        return handleExpression(expr,
                { Result.Error(line, "Integer in condition.") },
                { Result.Evaled(it) })
    }
}