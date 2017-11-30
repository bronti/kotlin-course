package ru.spbau.mit

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.RuleNode
import ru.spbau.mit.parser.SimpleParser
import ru.spbau.mit.parser.SimpleParserBaseVisitor
import java.io.Writer

class EvaluateVisitor(out: Writer) : SimpleParserBaseVisitor<EvaluateVisitor.Result>() {

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
        return ctx.children?.fold(defaultResult()) { prevResult, child ->
            if (prevResult is Result.Returned) return prevResult
            child.accept(this)
        } ?: Result.None
    }

    override fun visitBlockWithBraces(ctx: SimpleParser.BlockWithBracesContext): Result {
        context.goDeeper()
        val result = ctx.block()?.accept(this) ?: Result.None
        context.closeCurrentScope()
        return result
    }

    override fun visitVariableDeclaration(ctx: SimpleParser.VariableDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val wasNotDefined = context.scope.defineVariable(name)
        runtimeCheck(wasNotDefined, ctx, "Redefinition of variable $name.")
        val exprResult = ctx.expression() ?: return Result.None
        return evalIntExpression(exprResult, { context.scope.setVariable(name, it); Result.None }, "Only Integer variables are allowed.")

    }

    override fun visitReturnStatement(ctx: SimpleParser.ReturnStatementContext)
            = evalIntExpression(ctx.expression(), { Result.Returned(it) }, "Only Integer can be returned.")

    override fun visitVariableExpression(ctx: SimpleParser.VariableExpressionContext): Result {
        val name = ctx.ID().symbol.text
        return assertResultType<Result.ExpressionInt>(
                context.scope.getVariable(name)?.let(Result::ExpressionInt),
                ctx, "Undefined or unset variable $name."
        )
    }

    override fun visitLiteralExpression(ctx: SimpleParser.LiteralExpressionContext): Result {
        return assertResultType<Result.ExpressionInt>(
                ctx.LITERAL().symbol.text.toIntOrNull()?.let(Result::ExpressionInt),
                ctx,
                "Cannot read literal ${ctx.start.text}"
        )
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
        runtimeCheck(context.scope.isDefinedVariable(name), ctx, "There is no variable with name $name.")
        return evalIntExpression(
                ctx.expression(),
                { context.scope.setVariable(name, it); Result.None },
                "Cannot assign Bool value to variable."
        )
    }

    override fun visitFunctionDeclaration(ctx: SimpleParser.FunctionDeclarationContext): Result {
        val name = ctx.ID().symbol.text
        val parameters = ctx.parameterNames().ID().map { it.symbol.text }
        runtimeCheck(parameters.distinct().size == parameters.size, ctx, "Same param names in function are not allowed.")
        val body = ctx.blockWithBraces()
        val wasNotDefined = context.scope.defineFunction(name, parameters, body)
        runtimeCheck(wasNotDefined, ctx, "Redefenition of the function $name.")
        return Result.None
    }

    override fun visitFunctionCall(ctx: SimpleParser.FunctionCallContext): Result {
        val name = ctx.ID().symbol.text
        val arguments = ctx
                .arguments()
                .expression()
                .mapWithResultsType<Result.ExpressionInt>("Cannot pass Boolean as function argument.")
                .map(Result.ExpressionInt::value)

        val function = context.scope.getFunction(name)

        if (function == null) {
            runtimeCheck(context.callPredefinedFunction(name, arguments), ctx, "There is no such function")
            return Result.None
        }

        val (argNames, body) = function

        runtimeCheck(argNames.size == arguments.size, ctx, "Invalid number of arguments.")

        context.goDeeper()
        for ((argName, value) in argNames.zip(arguments)) {
            context.scope.defineVariable(argName)
            context.scope.setVariable(argName, value)
        }
        val result = body.accept(this) as? Result.Returned ?: Result.Returned(0)
        context.closeCurrentScope()
        return Result.ExpressionInt(result.value)
    }

    override fun visitFunctionCallExpression(ctx: SimpleParser.FunctionCallExpressionContext): Result
            = ctx.functionCall().accept(this)

    override fun visitParenthesesExpression(ctx: SimpleParser.ParenthesesExpressionContext): Result
            = ctx.expression().accept(this)



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

    override fun visitCompareExpression(ctx: SimpleParser.CompareExpressionContext): Result {
        val (left, right) = ctx
                .expression()
                .mapWithResultsType<Result.ExpressionInt>("Not Integer in compare.")
                .map(Result.ExpressionInt::value)
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

    override fun visitLogicExpression(ctx: SimpleParser.LogicExpressionContext): Result {
        val (left, right) = ctx
                .expression()
                .mapWithResultsType<Result.ExpressionBool>("Not Boolean in logical expression.")
                .map(Result.ExpressionBool::value)

        return Result.ExpressionBool(when {
            ctx.OR() != null -> left || right
            ctx.AND() != null -> left && right
            else -> throw IllegalStateException()
        })
    }

    private fun visitBinaryIntOp(exprs: List<SimpleParser.ExpressionContext>, op: (Int, Int) -> Result): Result {
        val (left, right) = exprs
                .mapWithResultsType<Result.ExpressionInt>("Not Int in binary expression.")
                .map(Result.ExpressionInt::value)
        return op(left, right)
    }

    private fun <T> evalIntExpression(exp: SimpleParser.ExpressionContext, handler: (Int) -> T, errorMsg: String): T
            = exp.visitWithResultType<Result.ExpressionInt>(errorMsg).value.let(handler)

    private fun evalCondition(exp: SimpleParser.ExpressionContext)
            = exp.visitWithResultType<Result.ExpressionBool>("Integer in condition.")

    private fun <T : Result> List<ParserRuleContext>.mapWithResultsType(msg: String): List<T> {
        return this.map {
            it.visitWithResultType<T>(msg)
        }
    }

    private fun <T : Result> ParserRuleContext.visitWithResultType(msg: String): T {
        return assertResultType(this.accept(this@EvaluateVisitor), this, msg)
    }

    private fun <T : Result> assertResultType(res: Result?, ctx: ParserRuleContext, msg: String): T {
        return res as? T ?: throw EvaluationException(ctx.start.line, msg)
    }

    private fun runtimeCheck(cond: Boolean, ctx: ParserRuleContext, msg: String) {
        if (!cond) {
            throw EvaluationException(ctx.start.line, msg)
        }
    }
}