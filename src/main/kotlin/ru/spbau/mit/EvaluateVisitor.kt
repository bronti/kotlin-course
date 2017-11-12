package ru.spbau.mit

import org.antlr.v4.runtime.tree.RuleNode
import ru.spbau.mit.parser.SimpleParser
import ru.spbau.mit.parser.SimpleParserBaseVisitor
import java.io.OutputStreamWriter

class EvaluateVisitor(out: OutputStreamWriter) : SimpleParserBaseVisitor<EvaluateVisitor.Result>() {

    private var context: Context = Context(out)

    class EvaluationException(val lineNumber: Int, msg: String): Exception(msg)

    sealed class Result {
        object None: Result()
        data class ExpressionInt(val value: Int): Result()
        data class ExpressionBool(val value: Boolean): Result()
        data class Returned(val value: Int): Result()
    }

    override fun defaultResult(): Result = Result.None

    override fun shouldVisitNextChild(node: RuleNode, currentResult: Result) = currentResult !is Result.Returned

    override fun visitFile(ctx: SimpleParser.FileContext): Result {
        val result = ctx.block().accept(this)
        return result as? Result.Returned ?: Result.Returned(0)
    }

    override fun visitBlock(ctx: SimpleParser.BlockContext): Result {
        return ctx.children.fold(defaultResult()) { prevResult, child ->
            if (prevResult is Result.Returned) return prevResult
            child.accept(this)
        }
    }

    override fun visitBlockWithBraces(ctx: SimpleParser.BlockWithBracesContext): Result {
        context.goDeeper()
        return ctx.block().accept(this)
    }

    override fun visitVariableDeclaration(ctx: SimpleParser.VariableDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        if (!context.scope.defineVariable(name)) throw EvaluationException(ctx.start.line, "Redefinition of variable $name.")
        val exprResult = ctx.expression() ?: return Result.None
        return evalIntExpression(exprResult, { context.scope.setVariable(name, it); Result.None }, "Only Integer variables are allowed.")

    }

    override fun visitReturnStatement(ctx: SimpleParser.ReturnStatementContext)
            = evalIntExpression(ctx.expression(), { Result.Returned(it) }, "Only Integer can be returned.")

    override fun visitVariableExpression(ctx: SimpleParser.VariableExpressionContext): Result {
        val name = ctx.ID().symbol.text
        return context.scope.getVariable(name)?.let { Result.ExpressionInt(it) }
                ?: throw EvaluationException(ctx.start.line, "Undefined or unset variable $name.")
    }

    override fun visitMultiplicationExpression(ctx: SimpleParser.MultiplicationExpressionContext): Result {
        return visitBinaryIntOp(ctx.expression()) { left, right ->
            val result = when {
                ctx.ASTERISK() != null -> left * right
                ctx.DIVISION() != null -> left / right
                ctx.MODULUS() != null -> left % right
                else -> throw IllegalStateException()
            }
            Result.ExpressionInt(result)
        }
    }

    override fun visitLiteralExpression(ctx: SimpleParser.LiteralExpressionContext): Result {
        return ctx.LITERAL().symbol.text.toIntOrNull()?.let { Result.ExpressionInt(it) }
                ?: throw EvaluationException(ctx.start.line, "Cannot read literal ${ctx.start.text}")
    }

    override fun visitSummExpression(ctx: SimpleParser.SummExpressionContext): Result {
        return visitBinaryIntOp(ctx.expression()) { left, right ->
            val result = when {
                ctx.PLUS() != null -> left + right
                ctx.MINUS() != null -> left - right
                else -> throw IllegalStateException()
            }
            Result.ExpressionInt(result)
        }
    }

    override fun visitWhileStatement(ctx: SimpleParser.WhileStatementContext): Result {
        while (evalCondition(ctx.expression()).value) {
            val body = ctx.blockWithBraces().accept(this)
            if (body is Result.Returned) return body
        }
        return Result.None
    }

    override fun visitIfStatement(ctx: SimpleParser.IfStatementContext): Result {
        if (evalCondition(ctx.expression()).value) {
            val body = ctx.blockWithBraces(0).accept(this)
            if (body is Result.Returned) return body
        } else if (ctx.ELSE() != null) {
            val elseBody = ctx.blockWithBraces(1).accept(this)
            if (elseBody is Result.Returned) return elseBody
        }
        return Result.None
    }

    override fun visitAssignment(ctx: SimpleParser.AssignmentContext): Result {
        val name = ctx.ID().symbol.text
        if (!context.scope.isDefinedVariable(name)) {
            throw EvaluationException(ctx.start.line, "There is no variable with name $name.")
        }
        return evalIntExpression(
                ctx.expression(),
                { context.scope.setVariable(name, it); Result.None },
                "Cannot assign Bool value to variable.")
    }

    override fun visitFunctionDeclaration(ctx: SimpleParser.FunctionDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val parameters = ctx.parameterNames().ID().map { it.symbol.text }
        if (parameters.distinct().size != parameters.size)
            throw EvaluationException(ctx.start.line, "Same param names in function is not allowed.")
        val body = ctx.blockWithBraces()
        if (!context.scope.defineFunction(name, parameters, body))
            throw EvaluationException(ctx.start.line, "Redefenition of the function $name.")
        return Result.None
    }

    override fun visitFunctionCall(ctx: SimpleParser.FunctionCallContext): Result {
        val name = ctx.ID().symbol.text
        val arguments = ctx.arguments().expression().map {
            val result = it.accept(this) as? Result.ExpressionInt
                    ?: throw EvaluationException(it.start.line, "Cannot pass Boolen as function argument.")
            result.value
        }

        val (argNames, body) = context.scope.getFunction(name) ?:
                if (context.callPredefinedFunction(name, arguments)) return Result.None
                else throw EvaluationException(ctx.start.line, "There is no such function")

        if (argNames.size != arguments.size)
            throw EvaluationException(ctx.start.line, "Invalid number of arguments.")

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
        val (left, right) = ctx.expression().map { it.accept(this).let {
            (it as? Result.ExpressionInt)?.value
                    ?: throw EvaluationException(ctx.start.line, "Not Integer in compare.")
        }}
        return Result.ExpressionBool(when {
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
        val (left, right) = ctx.expression().map { it.accept(this).let {
            (it as? Result.ExpressionBool)?.value
                    ?: throw EvaluationException(ctx.start.line, "Not Boolean in logical expression.")
        }}
        return Result.ExpressionBool(when {
            ctx.OR() != null -> left || right
            ctx.AND() != null -> left && right
            else -> throw IllegalStateException()
        })
    }

    private fun visitBinaryIntOp(exprs: List<SimpleParser.ExpressionContext>, op: (Int, Int) -> Result): Result {
        val (left, right) = exprs.map { it.accept(this).let {
            (it as? Result.ExpressionInt)?.value
                    ?: throw EvaluationException(exprs[0].start.line, "Not Boolean in logical expression.")
        }}
        return op(left, right)
    }

    private fun <T> evalIntExpression(exp: SimpleParser.ExpressionContext, handler: (Int) -> T, errorMsg: String): T {
        val result = exp.accept(this) as? Result.ExpressionInt
                ?: throw EvaluationException(exp.start.line, errorMsg)
        return handler(result.value)
    }

    private fun evalCondition(exp: SimpleParser.ExpressionContext): Result.ExpressionBool {
        val result = exp.accept(this) as? Result.ExpressionBool
                ?: throw EvaluationException(exp.start.line, "Integer in condition.")
        return Result.ExpressionBool(result.value)
    }
}