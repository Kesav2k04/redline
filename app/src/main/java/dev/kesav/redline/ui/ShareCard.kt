package dev.kesav.redline.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import dev.kesav.redline.ScanState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The words on the share card, apart from the drawing so a test can hold them to the one
 * rule that matters: a count leaves the phone, the lease does not.
 */
internal object ShareCardText {
    const val LINK = "github.com/Kesav2k04/redline"
    const val LINE = "in my lease could cost me money"
    const val FOOT = "Checked on my phone before signing. The lease was never uploaded."

    fun ofTotal(state: ScanState.Scanned) = "of ${state.clauseCount} clauses"

    fun legend(state: ScanState.Scanned): List<String> {
        val other = state.flaggedClauses - state.highClauses
        val clear = (state.clauseCount - state.flaggedClauses).coerceAtLeast(0)
        return listOfNotNull(
            "${state.highClauses} serious".takeIf { state.highClauses > 0 },
            "$other worth checking".takeIf { other > 0 },
            "$clear clear".takeIf { clear > 0 },
        )
    }

    /** The line that travels with the picture, for apps that show text beside an image. */
    fun message(state: ScanState.Scanned): String {
        val n = state.flaggedClauses
        val clauses = if (n == 1) "1 clause" else "$n clauses"
        return "Redline found $clauses in my lease that could cost me money. It read the " +
            "lease on my phone and never uploaded it. https://$LINK"
    }

    /** Offered only where the count means something: a lease with at least one finding. */
    fun offered(state: ScanState.Scanned) = state.looksLikeLease && state.flaggedClauses > 0
}

/**
 * The result as a picture, sized for a chat or a story (4:5).
 *
 * Someone who has just found eleven costly clauses in their lease tells a flatmate, and a
 * screenshot of the report would carry the clauses with it. This carries the count, the
 * split and where it came from, and nothing a landlord could recognise. Colours are fixed
 * rather than read from the theme, so a card sent from a phone in light mode looks the
 * same as one sent in dark mode.
 */
internal fun drawShareCard(state: ScanState.Scanned): Bitmap {
    val w = 1080
    val h = 1350
    val pad = 96f
    val ink = 0xFF111318.toInt()
    val paper = 0xFFF2F3F5.toInt()
    val muted = 0xFFB9BDC7.toInt()
    val red = 0xFFFF6B5E.toInt()
    val amber = 0xFFF2A541.toInt()
    val track = 0xFF2C3038.toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(ink)

    fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    // The wordmark, as on the first screen: a short red rule, then the name.
    fill.color = red
    c.drawRoundRect(RectF(pad, 112f, pad + 8f, 156f), 4f, 4f, fill)
    c.drawText("Redline", pad + 28f, 148f, paint(44f, paper, bold = true))

    // The count. The number is the result, so it is the largest thing on the card.
    val number = paint(300f, red, bold = true).apply { letterSpacing = -0.03f }
    val n = state.flaggedClauses.toString()
    val baseline = 560f
    c.drawText(n, pad - 8f, baseline, number)
    c.drawText(ShareCardText.ofTotal(state), pad + number.measureText(n) + 16f, baseline - 22f, paint(64f, paper, bold = true))

    val line = paint(64f, paper, bold = true)
    val lineLayout = StaticLayout.Builder.obtain(ShareCardText.LINE, 0, ShareCardText.LINE.length, line, (w - 2 * pad).toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.05f)
        .build()
    c.save()
    c.translate(pad, 600f)
    lineLayout.draw(c)
    c.restore()

    // The split, drawn the way the report draws it.
    val barTop = 620f + lineLayout.height + 72f
    val barH = 28f
    val serious = state.highClauses
    val other = state.flaggedClauses - serious
    val clear = (state.clauseCount - state.flaggedClauses).coerceAtLeast(0)
    val parts = listOf(serious to red, other to amber, clear to track).filter { it.first > 0 }
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val gap = 8f
    val usable = (w - 2 * pad) - gap * (parts.size - 1).coerceAtLeast(0)
    var x = pad
    for ((count, color) in parts) {
        val segment = usable * count / total
        fill.color = color
        c.drawRoundRect(RectF(x, barTop, x + segment, barTop + barH), barH / 2, barH / 2, fill)
        x += segment + gap
    }

    // Legend: a dot in each colour, then the count in words.
    val legendPaint = paint(36f, muted)
    var lx = pad
    val ly = barTop + barH + 64f
    for ((i, label) in ShareCardText.legend(state).withIndex()) {
        val color = parts.getOrNull(i)?.second ?: muted
        fill.color = color
        c.drawCircle(lx + 10f, ly - 12f, 10f, fill)
        c.drawText(label, lx + 32f, ly, legendPaint)
        lx += 32f + legendPaint.measureText(label) + 44f
    }

    // Where it came from, and the promise, at the foot.
    fill.color = track
    c.drawRect(pad, h - 250f, w - pad, h - 248f, fill)
    val foot = paint(36f, muted)
    val footLayout = StaticLayout.Builder.obtain(ShareCardText.FOOT, 0, ShareCardText.FOOT.length, foot, (w - 2 * pad).toInt())
        .setLineSpacing(0f, 1.15f)
        .build()
    c.save()
    c.translate(pad, h - 212f)
    footLayout.draw(c)
    c.restore()
    c.drawText(ShareCardText.LINK, pad, h - 72f, paint(36f, paper, bold = true))

    return bmp
}

/**
 * Draws the card off the main thread, writes it to the cache and wraps it in a chooser.
 *
 * The file sits in the cache under one fixed name, so each share overwrites the last and
 * nothing accumulates. It holds a count and a link, never the lease.
 */
internal suspend fun shareCardIntent(context: Context, state: ScanState.Scanned): Intent =
    withContext(Dispatchers.Default) {
        val bmp = drawShareCard(state)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "redline-result.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ShareCardText.message(state))
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Share the count").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
