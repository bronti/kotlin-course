package ru.spbau.mit

import kotlin.test.assertTrue

import org.junit.Test
import kotlin.test.assertFalse

class TestSource {
    @Test
    fun testPetr() {
        assertTrue(solveTheProblem("petr"))
    }

    @Test
    fun testBad() {
        assertFalse(solveTheProblem("etis atis animatis etis atis amatis"))
    }

    @Test
    fun testGood() {
        assertTrue(solveTheProblem("nataliala kataliala vetra feinites"))
    }
}
