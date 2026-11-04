package com.example.composetoolbox.feature.expense

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.example.composetoolbox.ui.formatMoney
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes expense reports. Called on Dispatchers.IO with an OutputStream obtained from
 * the Storage Access Framework (ActivityResultContracts.CreateDocument), so no storage
 * permission is needed.
 */
object ReportExporter {

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

    /** CSV that opens directly in Excel / Google Sheets. */
    fun writeCsv(out: OutputStream, expenses: List<Expense>, currency: String) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write("\uFEFF") // BOM so Excel detects UTF-8 (for ₹, €, £)
        writer.write("Date,Category,Note,Amount ($currency)\n")
        expenses.sortedBy { it.date }.forEach { e ->
            writer.write(
                listOf(
                    e.date.toString(),
                    e.category.label,
                    escapeCsv(e.note),
                    String.format(Locale.US, "%.2f", e.amount),
                ).joinToString(","),
            )
            writer.write("\n")
        }
        writer.write(",,TOTAL,${String.format(Locale.US, "%.2f", expenses.sumOf { it.amount })}\n")
        writer.flush()
    }

    /** A4 PDF with a category breakdown (coloured bars) and a paginated expense table. */
    fun writePdf(out: OutputStream, expenses: List<Expense>, currency: String) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val muted = Paint(body).apply { color = Color.GRAY }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin + 10f

        val colDate = margin
        val colCategory = margin + 90f
        val colNote = margin + 200f
        val colAmount = pageWidth - margin

        fun tableHeader() {
            canvas.drawText("Date", colDate, y, bold)
            canvas.drawText("Category", colCategory, y, bold)
            canvas.drawText("Note", colNote, y, bold)
            canvas.drawText("Amount", colAmount, y, rightBold)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, rule)
            y += 16f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin + 10f
                tableHeader()
            }
        }

        val total = expenses.sumOf { it.amount }

        canvas.drawText("Expense Report", margin, y, titlePaint)
        y += 20f
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
        canvas.drawText("Generated $generated  •  ${expenses.size} expenses", margin, y, muted)
        y += 28f
        canvas.drawText("Total spent: ${formatMoney(total, currency)}", margin, y, bold)
        y += 24f

        // Category breakdown
        val byCategory = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
        val barMaxWidth = pageWidth - margin * 2 - 200f
        byCategory.forEach { (category, amount) ->
            canvas.drawText(category.label, colDate, y, body)
            fill.color = category.color.toArgb()
            val width = if (total > 0) (amount / total * barMaxWidth).toFloat() else 0f
            canvas.drawRect(colCategory, y - 9f, colCategory + width, y + 1f, fill)
            canvas.drawText(formatMoney(amount, currency), colAmount, y, right)
            y += 18f
        }
        y += 16f

        tableHeader()
        expenses.sortedBy { it.date }.forEach { e ->
            ensureSpace(18f)
            canvas.drawText(e.date.format(dateFormat), colDate, y, body)
            canvas.drawText(e.category.label, colCategory, y, body)
            canvas.drawText(e.note.take(38), colNote, y, body)
            canvas.drawText(formatMoney(e.amount, currency), colAmount, y, right)
            y += 18f
        }
        ensureSpace(30f)
        canvas.drawLine(margin, y - 8f, pageWidth - margin, y - 8f, rule)
        y += 6f
        canvas.drawText("TOTAL", colNote, y, bold)
        canvas.drawText(formatMoney(total, currency), colAmount, y, rightBold)

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** Plain-text summary for the share sheet (WhatsApp, email, etc.). */
    fun buildTextSummary(expenses: List<Expense>, currency: String): String = buildString {
        appendLine("Expense summary (${expenses.size} expenses)")
        appendLine("Total: ${formatMoney(expenses.sumOf { it.amount }, currency)}")
        appendLine()
        expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .forEach { (category, amount) -> appendLine("• ${category.label}: ${formatMoney(amount, currency)}") }
    }

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
