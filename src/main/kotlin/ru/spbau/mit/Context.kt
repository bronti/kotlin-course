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
    private val allVariables = HashSet<String>()
    private val intVariables = HashMap<String, Int>()
    private val boolVariables = HashMap<String, Boolean>()

    fun defineVariable(name: String): Boolean {
        if (name in notSetVariables) return false
        notSetVariables.add(name)
        allVariables.add(name)
        return true
    }

    open fun <T> setVariable(name: String, value: T): Boolean {
        if (name !in allVariables) return false
        intVariables.remove(name)
        boolVariables.remove(name)
        when (value) {
            is Int -> intVariables[name] = value
            is Boolean -> boolVariables[name] = value
            else -> throw IllegalStateException()
        }
        notSetVariables.remove(name)
        return true
    }

    open fun getIntVariable(name: String): Int? {
        if (name !in intVariables) return null
        return intVariables[name]
    }

    open fun getBoolVariable(name: String): Boolean? {
        if (name !in boolVariables) return null
        return boolVariables[name]
    }

    open fun getVariableType(name: String) = when (name) {
        in boolVariables -> VariableType.BOOL
        in intVariables -> VariableType.INT
        else -> VariableType.NONE
    }

    class Base : Scope()
    data class Inner(val outer: Scope) : Scope() {
        override fun <T> setVariable(name: String, value: T)
                = super.setVariable(name, value) || outer.setVariable(name, value)

        override fun getIntVariable(name: String): Int?
                = super.getIntVariable(name) ?: outer.getIntVariable(name)

        override fun getBoolVariable(name: String): Boolean?
                = super.getBoolVariable(name) ?: outer.getBoolVariable(name)

        override fun getVariableType(name: String): VariableType {
            val res = super.getVariableType(name)
            if (res != VariableType.NONE) return res
            return outer.getVariableType(name)
        }
    }

    companion object {
        val empty get() = Base()
    }
}