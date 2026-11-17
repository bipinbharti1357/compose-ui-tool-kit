package com.example.composetoolbox.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatMoneyTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(originalLocale)

    @Test
    fun wholeAmounts_haveNoDecimals() {
        assertEquals("₹1,234", formatMoney(1_234.0, "₹"))
        assertEquals("$0", formatMoney(0.0, "$"))
    }

    @Test
    fun fractionalAmounts_haveTwoDecimals() {
        assertEquals("€1,850.50", formatMoney(1_850.5, "€"))
        assertEquals("£0.99", formatMoney(0.99, "£"))
    }

    @Test
    fun largeAmounts_useGroupingSeparators() {
        assertEquals("₹1,234,567", formatMoney(1_234_567.0, "₹"))
    }
}
