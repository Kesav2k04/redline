package dev.kesav.redline.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.kesav.redline.ScanState
import dev.kesav.redline.Severity
import java.io.File

/**
 * The full report as a page someone can put in front of a landlord or an adviser.
 *
 * Plain text travels everywhere and stays the body of the message. A reader on the panel
 * still asked for "something that looks like it came from somewhere", so the same content
 * is laid out as A4 pages the way the app lays it out: the count, then each clause with its
 * margin mark, what is wrong, what to ask for, and the lease's own words. Built on the phone
 * with the platform's PdfDocument, written to the cache under one fixed name, and sent only
 * when the reader sends it.
 */
internal object ReportPdf {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 48f
    private val INK = 0xFF111318.toInt()
    private val MUTED = 0xFF585D6B.toInt()
    private val RULE = 0xFFDFE1E7.toInt()
    private val RED = 0xFFCC2B22.toInt()
    private val AMBER = 0xFFA85A06.toInt()
    private val TINT = 0xFFF1F2F5.toInt()

    fun write(context: Context, state: ScanState.Scanned): File {
        val doc = PdfDocument()
        val pages = Pages(doc)

        // Header band: the verdict, as on the screen.
        pages.need(120f)
        val c0 = pages.canvas
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        c0.drawRoundRect(RectF(M, pages.y, W - M, pages.y + 104f), 14f, 14f, band)
        c0.drawRoundRect(RectF(M + 20f, pages.y + 20f, M + 23f, pages.y + 34f), 1.5f, 1.5f, Paint().apply { color = 0xFFFF6B5E.toInt() })
        c0.drawText("Redline", M + 30f, pages.y + 32f, text(11f, 0xFFF2F3F5.toInt(), bold = true))
        val flagged = state.flaggedClauses
        c0.drawText("$flagged", M + 20f, pages.y + 82f, text(40f, 0xFFFF6B5E.toInt(), bold = true))
        val nWidth = text(40f, 0, bold = true).measureText("$flagged")
        c0.drawText(
            "of ${state.clauseCount} clauses in this lease will cost money",
            M + 28f + nWidth, pages.y + 80f, text(14f, 0xFFF2F3F5.toInt(), bold = true),
        )
        pages.y += 104f + 24f

        for ((i, group) in state.groups.withIndex()) {
            val worst = group.worst
            val mark = if (worst == Severity.HIGH) RED else AMBER
            val topic = group.findings.first().topic
            val label = (if (worst == Severity.HIGH) "Serious" else "Worth checking") + "  ·  " + topic

            val labelLayout = layout(label, 9.5f, MUTED, bold = true)
            val body = group.findings.map { f ->
                Triple(
                    layout(f.headline, 12.5f, INK, bold = true),
                    layout(f.reason, 10f, MUTED),
                    if (f.ask.isBlank()) null else layout("Ask for ${f.ask}.", 10f, INK, inset = 16f),
                )
            }
            val quote = layout(group.clause.text.replace(Regex("\\s+"), " ").trim(), 9.5f, MUTED, serif = true, inset = 12f)

            val bodyHeight = body.sumOf { (h, r, a) ->
                (h.height + 4 + r.height + (a?.let { it.height + 18 } ?: 0) + 10).toDouble()
            }.toFloat()
            val blockHeight = labelLayout.height + 8f + bodyHeight + quote.height + 20f
            pages.need(minOf(blockHeight, H - 2 * M - 40f))

            val c = pages.canvas
            draw(c, labelLayout, M, pages.y)
            pages.y += labelLayout.height + 8f

            // The margin mark beside what the scanner says.
            val markTop = pages.y
            for ((h, r, a) in body) {
                draw(c, h, M + 14f, pages.y); pages.y += h.height + 4f
                draw(c, r, M + 14f, pages.y); pages.y += r.height
                if (a != null) {
                    pages.y += 6f
                    val box = RectF(M + 14f, pages.y, W - M, pages.y + a.height + 12f)
                    c.drawRoundRect(box, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TINT })
                    draw(c, a, M + 14f, pages.y + 6f)
                    pages.y += a.height + 12f
                }
                pages.y += 10f
            }
            c.drawRoundRect(RectF(M, markTop, M + 3f, pages.y - 10f), 1.5f, 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mark })

            // The lease's own words, set like the document they came from.
            c.drawRect(M + 14f, pages.y, M + 16f, pages.y + quote.height, Paint().apply { color = RULE })
            draw(c, quote, M + 14f, pages.y)
            pages.y += quote.height + 12f

            if (i < state.groups.size - 1) {
                c.drawRect(M, pages.y, W - M, pages.y + 0.75f, Paint().apply { color = RULE })
                pages.y += 16f
            }
        }

        pages.finish()
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "redline-report.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private const val FOOT =
        "Found with Redline, which matches clauses against a fixed set of rules. It catches what " +
            "those rules describe and nothing else, so this is a list of things to ask about rather " +
            "than legal advice."

    /** Page handling: a new page when the next block will not fit, with the footer on each. */
    private class Pages(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        var y = M
        val canvas: Canvas get() = page!!.canvas

        init { next() }

        fun need(height: Float) {
            if (y + height > H - M - 40f) next()
        }

        private fun next() {
            page?.let { footer(it.canvas); doc.finishPage(it) }
            number += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            y = M
        }

        fun finish() {
            page?.let { footer(it.canvas); doc.finishPage(it) }
            page = null
        }

        private fun footer(c: Canvas) {
            val foot = layout(FOOT, 7.5f, MUTED, width = (W - 2 * M - 40f).toInt())
            draw(c, foot, M, H - M - foot.height + 8f)
            val n = text(7.5f, MUTED)
            val label = "$number"
            c.drawText(label, W - M - n.measureText(label), H - M + 6f, n)
        }
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, serif: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when {
                serif -> Typeface.SERIF
                bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
        }

    private fun layout(
        s: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        serif: Boolean = false,
        inset: Float = 0f,
        width: Int = (W - 2 * M - 14f).toInt(),
    ): StaticLayout {
        val paint = text(size, color, bold, serif)
        return StaticLayout.Builder.obtain(s, 0, s.length, paint, (width - 2 * inset).toInt().coerceAtLeast(40))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setIndents(intArrayOf(inset.toInt()), intArrayOf(inset.toInt()))
            .build()
    }

    private fun draw(c: Canvas, l: StaticLayout, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        l.draw(c)
        c.restore()
    }
}
