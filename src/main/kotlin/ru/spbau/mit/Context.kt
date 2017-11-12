package ru.spbau.mit


class Context {
    var scope: Scope = Scope.empty
        private set

    fun goDeeper() {
        scope = Scope.Inner(scope)
    }

    fun closeCurrentScope() {
        scope = (scope as Scope.Inner).outer
    }
}

sealed class Scope {

    enum class VariableType {
        NONE, INT, BOOL
    }

    private val notSetVariables = HashSet<String>()
    private val variableValues = HashMap<String, Int>()

    fun defineVariable(name: String): Boolean {
        if (name in notSetVariables) return false
        notSetVariables.add(name)
        return true
    }

    open fun setVariable(name: String, value: Int): Boolean {
        if (!isDefinedVariable(name)) return false
        variableValues[name] = value
        notSetVariables.remove(name)
        return true
    }

    open fun getVariable(name: String): Int? {
        if (name !in variableValues) return null
        return variableValues[name]
    }

    open fun isDefinedVariable(name: String) = name in notSetVariables || name in variableValues

    class Base : Scope()
    data class Inner(val outer: Scope) : Scope() {
        override fun setVariable(name: String, value: Int)
                = super.setVariable(name, value) || outer.setVariable(name, value)

        override fun getVariable(name: String): Int?
                = super.getVariable(name) ?: outer.getVariable(name)

        override fun isDefinedVariable(name: String)
                = super.isDefinedVariable(name) || outer.isDefinedVariable(name)
    }

    companion object {
        val empty get() = Base()
    }
}