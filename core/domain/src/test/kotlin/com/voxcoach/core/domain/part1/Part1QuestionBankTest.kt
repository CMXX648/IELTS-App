package com.voxcoach.core.domain.part1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Part1QuestionBankTest {
    @Test
    fun pickSession_returnsExactCount_andDeterministicForSeed() {
        val a = Part1QuestionBank.pickSession(count = 5, seed = 42L)
        val b = Part1QuestionBank.pickSession(count = 5, seed = 42L)
        assertEquals(5, a.size)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertTrue(a.all { it.text.isNotBlank() })
    }

    @Test
    fun pickSession_usesOneOrTwoThemes() {
        val q = Part1QuestionBank.pickSession(count = 5, seed = 7L)
        val themes = q.map { it.themeId }.distinct()
        assertTrue(themes.size in 1..2)
    }

    @Test
    fun pickSession_fourQuestionsOk() {
        val q = Part1QuestionBank.pickSession(count = 4, seed = 1L)
        assertEquals(4, q.size)
    }
}
