package com.voxcoach.core.domain.part2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Part2CueCardBankTest {
    @Test
    fun bank_hasAtLeastSixCards_coveringFiveCategories() {
        assertTrue(Part2CueCardBank.all.size >= 6)
        val cats = Part2CueCardBank.all.map { it.category }.toSet()
        assertTrue(cats.containsAll(Part2Category.entries.toSet()))
    }

    @Test
    fun pick_isDeterministicForSeed() {
        val a = Part2CueCardBank.pick(seed = 42L)
        val b = Part2CueCardBank.pick(seed = 42L)
        assertEquals(a.id, b.id)
        assertTrue(a.title.isNotBlank())
        assertTrue(a.bullets.size >= 3)
        assertTrue(a.explain.isNotBlank())
    }

    @Test
    fun pickByCategory_returnsMatchingCategory() {
        Part2Category.entries.forEach { cat ->
            val card = Part2CueCardBank.pickByCategory(cat, seed = 7L)
            assertEquals(cat, card.category)
        }
    }
}
