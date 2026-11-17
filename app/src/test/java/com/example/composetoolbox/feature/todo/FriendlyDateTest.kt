package com.example.composetoolbox.feature.todo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FriendlyDateTest {

    private lateinit var originalLocale: Locale
    private val today = LocalDate.of(2026, 11, 12)

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(originalLocale)

    @Test
    fun relativeDays() {
        assertEquals("Today", friendlyDate(today, today))
        assertEquals("Tomorrow", friendlyDate(today.plusDays(1), today))
        assertEquals("Yesterday", friendlyDate(today.minusDays(1), today))
    }

    @Test
    fun sameYear_omitsYear() {
        assertEquals("Fri, Nov 6", friendlyDate(LocalDate.of(2026, 11, 6), today))
    }

    @Test
    fun otherYear_includesYear() {
        assertEquals("Fri, Jan 1, 2027", friendlyDate(LocalDate.of(2027, 1, 1), today))
    }
}
