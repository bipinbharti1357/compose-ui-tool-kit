package com.example.composetoolbox.feature.expense

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale

class ReportExporterTest {

    private lateinit var originalLocale: Locale

    private val expenses = listOf(
        Expense(1, 120.0, ExpenseCategory.TRANSPORT, "Auto ride", LocalDate.of(2026, 3, 5)),
        Expense(2, 450.5, ExpenseCategory.FOOD, "Lunch, team", LocalDate.of(2026, 3, 1)),
        Expense(3, 80.0, ExpenseCategory.FOOD, "Said \"hi\"", LocalDate.of(2026, 3, 3)),
    )

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(originalLocale)

    private fun csv(list: List<Expense>, currency: String = "₹"): String =
        ByteArrayOutputStream().also { ReportExporter.writeCsv(it, list, currency) }.toString(Charsets.UTF_8.name())

    @Test
    fun csv_startsWithBomAndHeader() {
        val text = csv(expenses)
        assertTrue(text.startsWith("﻿"))
        assertEquals("Date,Category,Note,Amount (₹)", text.removePrefix("﻿").lines().first())
    }

    @Test
    fun csv_rowsAreSortedByDate_escaped_andTotalled() {
        val lines = csv(expenses, "$").removePrefix("﻿").trimEnd('\n').lines()
        assertEquals(
            listOf(
                "Date,Category,Note,Amount ($)",
                "2026-03-01,Food,\"Lunch, team\",450.50",
                "2026-03-03,Food,\"Said \"\"hi\"\"\",80.00",
                "2026-03-05,Transport,Auto ride,120.00",
                ",,TOTAL,650.50",
            ),
            lines,
        )
    }

    @Test
    fun csv_emptyList_hasHeaderAndZeroTotal() {
        val lines = csv(emptyList()).removePrefix("﻿").trimEnd('\n').lines()
        assertEquals(listOf("Date,Category,Note,Amount (₹)", ",,TOTAL,0.00"), lines)
    }

    @Test
    fun textSummary_listsTotalAndCategoriesByAmount() {
        val summary = ReportExporter.buildTextSummary(expenses, "₹")
        assertEquals(
            listOf(
                "Expense summary (3 expenses)",
                "Total: ₹650.50",
                "",
                "• Food: ₹530.50",
                "• Transport: ₹120",
            ),
            summary.trimEnd('\n').lines(),
        )
    }
}
