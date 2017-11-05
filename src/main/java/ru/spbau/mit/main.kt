package ru.spbau.mit

object PetrovskiyLanguage {

    enum class Sex {
        FEMALE, MALE
    }

    enum class Part {
        ADJECTIVE, NOUN, VERB
    }

    private val endingToSignature = hashMapOf(
            "lios" to Pair(Part.ADJECTIVE, Sex.MALE),
            "liala" to Pair(Part.ADJECTIVE, Sex.FEMALE),
            "etr" to Pair(Part.NOUN, Sex.MALE),
            "etra" to Pair(Part.NOUN, Sex.FEMALE),
            "initis" to Pair(Part.VERB, Sex.MALE),
            "inites" to Pair(Part.VERB, Sex.FEMALE)
    )

    private fun getEnding(str: String) = endingToSignature.keys.find { str.endsWith(it) }

    fun getSignature(str: String): Pair<Part, Sex>? {
        val ending = getEnding(str) ?: return null
        return endingToSignature[ending]
    }

    private fun isValidWord(str: String) = getEnding(str) != null

    fun isValidSentence(words: List<String>): Boolean {
        if (words.isEmpty()) return false
        if (words.size == 1) return isValidWord(words.single())

        val (firstPart, firstSex) = getSignature(words[0]) ?: return false
        if (firstPart == Part.VERB) return false

        tailrec fun checkSentence(prevPart: Part, prevSex: Sex, tail: List<String>): Boolean {
            if (tail.isEmpty()) return prevPart != Part.ADJECTIVE
            val (part, sex) = getSignature(tail[0]) ?: return false
            if (sex != prevSex
                    || (prevPart == Part.ADJECTIVE && part == Part.VERB)
                    || (prevPart != Part.ADJECTIVE && part != Part.VERB)) {
                return false
            }
            return checkSentence(part, sex, tail.drop(1))
        }

        return checkSentence(firstPart, firstSex, words.drop(1))
    }
}

fun toSentence(str: String): List<String> = str.split(' ')

fun solveTheProblem(str: String): Boolean {
    val sentence = toSentence(str)
    return PetrovskiyLanguage.isValidSentence(sentence)
}

fun main(args: Array<String>) {
    val result = if (solveTheProblem(readLine()!!)) "YES" else "NO"
    print(result)
}
