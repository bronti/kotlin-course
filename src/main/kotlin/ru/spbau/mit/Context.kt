package ru.spbau.mit

import ru.spbau.mit.parser.SimpleParser
import java.io.Writer


class Context(private val out: Writer) {
    var scope: Scope = Scope.empty
        private set

    fun goDeeper() {
        scope = Scope.Inner(scope)
    }

    fun closeCurrentScope() {
        scope = (scope as Scope.Inner).outer
    }

    private val predefinedFunctions =
            hashMapOf<String, (List<Int>) -> Unit>(
                    "println" to { it -> out.write(it.joinToString(" ", postfix = "\n")) }
            )

    fun callPredefinedFunction(name: String, args: List<Int>): Boolean {
        if (name !in predefinedFunctions) return false
        predefinedFunctions[name]!!(args)
        return true
    }
}

sealed class Scope {

    private val notSetVariables = HashSet<String>()
    private val variableValues = HashMap<String, Int>()

    private val functions = HashMap<String, Pair<List<String>, SimpleParser.BlockWithBracesContext>>()

    private fun definedInOuterScope(name: String) = name in notSetVariables || name in variableValues

    fun defineVariable(name: String): Boolean {
        if (definedInOuterScope(name)) return false
        notSetVariables.add(name)
        return true
    }

    private fun setVariableInOuterScope(name: String, value: Int): Boolean {
        if (!definedInOuterScope(name)) return false
        variableValues[name] = value
        notSetVariables.remove(name)
        return true
    }

    private fun getVariableFromOuterScope(name: String): Int? {
        if (name !in variableValues) return null
        return variableValues[name]
    }

    open fun getFunction(name: String): Pair<List<String>, SimpleParser.BlockWithBracesContext>? {
        return functions[name]
    }

    open fun setVariable(name: String, value: Int): Boolean = setVariableInOuterScope(name, value)
    open fun getVariable(name: String): Int? = getVariableFromOuterScope(name)

    open fun isDefinedVariable(name: String) = name in notSetVariables || name in variableValues

    fun defineFunction(name: String, params: List<String>, ctx: SimpleParser.BlockWithBracesContext): Boolean {
        if (name in functions) return false
        functions[name] = Pair(params, ctx)
        return true
    }

    class Base : Scope()

    data class Inner(val outer: Scope) : Scope() {
        override fun setVariable(name: String, value: Int)
                = super.setVariable(name, value) || outer.setVariable(name, value)

        override fun getVariable(name: String): Int?
                = super.getVariable(name) ?: outer.getVariable(name)

        override fun isDefinedVariable(name: String)
                = super.isDefinedVariable(name) || outer.isDefinedVariable(name)

        override fun getFunction(name: String)
                = super.getFunction(name) ?: outer.getFunction(name)
    }

    companion object {
        val empty get() = Base()
    }
}