package dev.tex.cruscotto.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Category
import dev.tex.cruscotto.data.Kind
import dev.tex.cruscotto.data.Txn
import dev.tex.cruscotto.ui.components.fmtDate
import dev.tex.cruscotto.ui.components.fmtNumeric
import dev.tex.cruscotto.ui.components.money
import dev.tex.cruscotto.ui.components.monthTitle
import dev.tex.cruscotto.ui.components.pct
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth

/**
 * A4 report of one year's transactions: summary, totals by category, then every transaction
 * grouped by month with monthly subtotals. Drawn with Android's own PdfDocument, entirely offline.
 * [context] must be in the app's language (the activity's context is).
 */
class YearPdf(private val context: Context) {
    private val pageW = 595 // A4 in PostScript points
    private val pageH = 842
    private val margin = 40f
    private val contentW = pageW - 2 * margin

    // light palette of the app: pastel azure, green and orange on white
    private val ink = Color.rgb(0x1C, 0x2A, 0x35)
    private val muted = Color.rgb(0x5E, 0x71, 0x80)
    private val line = Color.rgb(0xDC, 0xE6, 0xED)
    private val zebra = Color.rgb(0xF5, 0xF9, 0xFC)
    private val incomeColor = Color.rgb(0x2E, 0x8B, 0x5E)
    private val badColor = Color.rgb(0xC0, 0x39, 0x2B)
    private val tileColors = intArrayOf(Color.rgb(0xD5, 0xEC, 0xFA), Color.rgb(0xD3, 0xF0, 0xE2), Color.rgb(0xFF, 0xE3, 0xCC))

    private val serif: Typeface = ResourcesCompat.getFont(context, R.font.fraunces_semibold) ?: Typeface.SERIF
    private val sans: Typeface = ResourcesCompat.getFont(context, R.font.manrope_regular) ?: Typeface.SANS_SERIF
    private val sansBold: Typeface = ResourcesCompat.getFont(context, R.font.manrope_bold) ?: Typeface.DEFAULT_BOLD

    private fun paint(tf: Typeface, size: Float, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color
        fontFeatureSettings = "tnum" // aligned figures
    }

    private val pTitle = paint(serif, 26f)
    private val pH2 = paint(serif, 15f)
    private val pLabel = paint(sansBold, 7.5f, muted).apply { letterSpacing = 0.12f }
    private val pBody = paint(sans, 9f)
    private val pBodyMuted = paint(sans, 8f, muted)
    private val pBold = paint(sansBold, 9f)
    private val pTile = paint(sansBold, 14f)

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var pageNo = 0
    private var y = 0f
    private var year = 0

    fun write(out: OutputStream, year: Int, txns: List<Txn>, categories: List<Category>) {
        this.year = year
        doc = PdfDocument()
        try {
            newPage(first = true)
            val sorted = txns.sortedWith(compareBy<Txn> { it.epochDay }.thenBy { it.note })
            val income = sorted.filter { it.type == Kind.IN }.sumOf { it.amountCents }
            val expenses = sorted.filter { it.type == Kind.OUT }.sumOf { it.amountCents }
            summary(income, expenses)
            if (sorted.isEmpty()) {
                y += 20f
                canvas.drawText(context.getString(R.string.pdf_none, year), margin, y, pBody)
            } else {
                byCategory(sorted, categories, Kind.OUT, R.string.pdf_expenses_by_cat, expenses)
                byCategory(sorted, categories, Kind.IN, R.string.pdf_income_by_cat, income)
                transactions(sorted, categories)
            }
            finishPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ---------- layout ----------
    private fun newPage(first: Boolean = false) {
        finishPage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        page = p
        canvas = p.canvas
        // accent stripe across the top, as on the app's cards
        val stripe = Paint().apply {
            shader = LinearGradient(0f, 0f, pageW.toFloat(), 0f,
                intArrayOf(Color.rgb(0x7D, 0xBD, 0xE8), Color.rgb(0x86, 0xD1, 0xAE), Color.rgb(0xF7, 0xB2, 0x7A)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, pageW.toFloat(), 6f, stripe)
        y = margin + 6f
        if (first) {
            y += 18f
            canvas.drawText(context.getString(R.string.pdf_title, year), margin, y, pTitle)
            y += 16f
            canvas.drawText(context.getString(R.string.pdf_generated, fmtDate(LocalDate.now().toEpochDay())), margin, y, pBodyMuted)
            y += 22f
        } else {
            y += 8f
            canvas.drawText(context.getString(R.string.pdf_title, year), margin, y, pBodyMuted)
            y += 18f
        }
    }

    private fun finishPage() {
        val p = page ?: return
        val footer = context.getString(R.string.pdf_page, pageNo)
        canvas.drawText("Cruscotto", margin, pageH - 24f, pBodyMuted)
        canvas.drawText(footer, pageW - margin - pBodyMuted.measureText(footer), pageH - 24f, pBodyMuted)
        doc.finishPage(p)
        page = null
    }

    /** Starts a new page if fewer than [needed] points are left. */
    private fun ensure(needed: Float) {
        if (y + needed > pageH - 50f) newPage()
    }

    private fun summary(income: Long, expenses: Long) {
        val gap = 10f
        val w = (contentW - 2 * gap) / 3
        val h = 52f
        val items = listOf(
            Triple(context.getString(R.string.kpi_income), money(income), incomeColor),
            Triple(context.getString(R.string.kpi_expenses), money(expenses), ink),
            Triple(context.getString(R.string.kpi_balance), money(income - expenses), if (income - expenses < 0) badColor else incomeColor),
        )
        items.forEachIndexed { i, (label, value, valueColor) ->
            val x = margin + i * (w + gap)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tileColors[i] }
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), 10f, 10f, bg)
            canvas.drawText(label.uppercase(), x + 12f, y + 18f, pLabel)
            pTile.color = valueColor
            canvas.drawText(value, x + 12f, y + 40f, pTile)
        }
        pTile.color = ink
        y += h + 26f
    }

    private fun sectionTitle(text: String) {
        ensure(40f)
        canvas.drawText(text, margin, y, pH2)
        y += 8f
        canvas.drawLine(margin, y, margin + contentW, y, Paint().apply { color = line; strokeWidth = 0.8f })
        y += 14f
    }

    private fun byCategory(txns: List<Txn>, cats: List<Category>, kind: String, title: Int, total: Long) {
        val names = cats.filter { it.kind == kind }.associate { it.id to it.name }
        val rows = txns.filter { it.type == kind }
            .groupBy { names[it.categoryId] ?: context.getString(R.string.no_category) }
            .map { (name, list) -> name to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
        if (rows.isEmpty()) return
        sectionTitle(context.getString(title))
        val barMax = rows.first().second.coerceAtLeast(1)
        val barX = margin + 190f
        val barW = contentW - 190f - 130f
        for ((name, sum) in rows) {
            ensure(16f)
            canvas.drawText(fit(name, pBody, 180f), margin, y, pBody)
            // proportional bar in the theme's gradient
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = zebra }
            canvas.drawRoundRect(RectF(barX, y - 7f, barX + barW, y - 1f), 3f, 3f, track)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val c = if (kind == Kind.IN) intArrayOf(Color.rgb(0x9F, 0xD8, 0xBE), Color.rgb(0x5D, 0xB8, 0x8F))
                    else intArrayOf(Color.rgb(0xFF, 0xC9, 0x9B), Color.rgb(0xF2, 0xA1, 0x60))
                shader = LinearGradient(barX, 0f, barX + barW, 0f, c, null, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(RectF(barX, y - 7f, barX + barW * sum / barMax, y - 1f), 3f, 3f, fill)
            val share = pct(if (total > 0) sum * 100.0 / total else 0.0)
            canvas.drawText(share, barX + barW + 10f, y, pBodyMuted)
            val amount = money(sum)
            canvas.drawText(amount, margin + contentW - pBold.measureText(amount), y, pBold)
            y += 16f
        }
        y += 14f
    }

    private fun transactions(txns: List<Txn>, cats: List<Category>) {
        sectionTitle(context.getString(R.string.pdf_transactions))
        val catName = { t: Txn -> cats.firstOrNull { it.id == t.categoryId && it.kind == t.type }?.name ?: context.getString(R.string.no_category) }
        val colDate = margin
        val colDesc = margin + 62f
        val colCat = margin + 290f
        val amountRight = margin + contentW
        txns.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.toSortedMap().forEach { (month, list) ->
            ensure(60f)
            // month header with its totals
            val inc = list.filter { it.type == Kind.IN }.sumOf { it.amountCents }
            val out = list.filter { it.type == Kind.OUT }.sumOf { it.amountCents }
            canvas.drawText(monthTitle(month), margin, y, pBold)
            val totals = context.getString(R.string.pdf_month_totals, money(inc), money(out))
            canvas.drawText(totals, amountRight - pBodyMuted.measureText(totals), y, pBodyMuted)
            y += 12f
            header(colDate, colDesc, colCat, amountRight)
            list.forEachIndexed { i, t ->
                if (y + 15f > pageH - 50f) {
                    newPage()
                    header(colDate, colDesc, colCat, amountRight)
                }
                if (i % 2 == 0) canvas.drawRect(margin - 4f, y - 10f, amountRight + 4f, y + 4f, Paint().apply { color = zebra })
                canvas.drawText(fmtNumeric(t.epochDay), colDate, y, pBody)
                canvas.drawText(fit(t.note.ifBlank { catName(t) }, pBody, colCat - colDesc - 10f), colDesc, y, pBody)
                canvas.drawText(fit(catName(t), pBodyMuted, amountRight - 90f - colCat), colCat, y, pBodyMuted)
                val income = t.type == Kind.IN
                val amount = (if (income) "+" else "−") + money(t.amountCents)
                val p = if (income) Paint(pBold).apply { color = incomeColor } else pBold
                canvas.drawText(amount, amountRight - p.measureText(amount), y, p)
                y += 14f
            }
            y += 14f
        }
    }

    private fun header(colDate: Float, colDesc: Float, colCat: Float, amountRight: Float) {
        canvas.drawText(context.getString(R.string.pdf_col_date).uppercase(), colDate, y, pLabel)
        canvas.drawText(context.getString(R.string.pdf_col_description).uppercase(), colDesc, y, pLabel)
        canvas.drawText(context.getString(R.string.pdf_col_category).uppercase(), colCat, y, pLabel)
        val a = context.getString(R.string.pdf_col_amount).uppercase()
        canvas.drawText(a, amountRight - pLabel.measureText(a), y, pLabel)
        y += 14f
    }

    /** Cuts [text] with an ellipsis so it fits in [width]. */
    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        val n = p.breakText(text, true, width - p.measureText("…"), null)
        return text.take(n).trimEnd() + "…"
    }
}
