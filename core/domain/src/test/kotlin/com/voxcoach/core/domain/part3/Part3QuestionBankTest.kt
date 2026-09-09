package com.voxcoach.core.domain.part3

import com.voxcoach.core.domain.part2.Part2Category
import com.voxcoach.core.domain.part2.Part2CueCardBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Part3QuestionBankTest {
    @Test
    fun bank_coversAllPart2Categories_withAtLeastFiveEach() {
        Part2Category.entries.forEach { cat ->
            val qs = Part3QuestionBank.forCategory(cat)
            assertTrue("$cat should have >=5 questions", qs.size >= 5)
            assertTrue(qs.all { it.category == cat })
        }
    }

    @Test
    fun pickForCategory_isDeterministicAndSized() {
        val a = Part3QuestionBank.pickForCategory(Part2Category.PLACE, count = 5, seed = 42L)
        val b = Part3QuestionBank.pickForCategory(Part2Category.PLACE, count = 5, seed = 42L)
        assertEquals(5, a.size)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertTrue(a.all { it.category == Part2Category.PLACE })
    }

    @Test
    fun pickSession_linksCueCardCategoryToFollowUps() {
        val (card, qs) = Part3QuestionBank.pickSession(count = 5, seed = 99L)
        assertEquals(5, qs.size)
        assertTrue(qs.all { it.category == card.category })
        assertTrue(Part2CueCardBank.all.any { it.id == card.id })
    }
}
