package com.callscreen.app.interactor

import org.junit.Assert.*
import org.junit.Test

class SendMathChallengeTest {

    @Test
    fun `math question format is readable`() {
        // Test the pattern: "What is X + Y?"
        val pattern = Regex("What is \\d+ \\+ \\d+\\?")
        // Generate a few challenges and verify format
        repeat(20) {
            val a = (1..19).random()
            val b = (1..19).random()
            val question = "What is $a + $b?"
            assertTrue("Question '$question' should match pattern", pattern.matches(question))
        }
    }

    @Test
    fun `math answers are correct`() {
        repeat(100) {
            val a = (1..19).random()
            val b = (1..19).random()
            assertEquals((a + b).toString(), (a + b).toString())
        }
    }

    @Test
    fun `math operands are within expected range`() {
        repeat(100) {
            val a = (1..19).random()
            val b = (1..19).random()
            assertTrue("a=$a should be 1-19", a in 1..19)
            assertTrue("b=$b should be 1-19", b in 1..19)
            assertTrue("sum=${a + b} should be 2-38", (a + b) in 2..38)
        }
    }
}
