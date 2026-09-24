package dev.kesav.redline.ui.camera

import kotlin.math.hypot

/**
 * One block of recognised text, by its edges on the preview, in view pixels, and [key], its
 * letters and digits, which is how the same paragraph is recognised in the next frame.
 */
internal data class Block(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val key: String = "",
) {
    val centreX: Float get() = (left + right) / 2
    val centreY: Float get() = (top + bottom) / 2
}

/** How the page in view looks to the recogniser. */
internal enum class Framing {
    /** Too little text in view to be a page: a label, a sign, a blank wall. */
    NONE,
    /** A page's worth of text, still moving. */
    FOUND,
    /** A page's worth of text, held still for long enough to photograph sharply. */
    STEADY,
}

/**
 * Judges successive analysed frames as [Framing].
 *
 * Text counts as found once at least [minChars] letters and digits are readable, which a
 * page of a lease clears many times over and a box label or a street sign does not. It is
 * steady once the blocks have stayed within [tolerance] of the view's diagonal for
 * [holdFrames] comparisons in a row. The movement measured is the median over blocks,
 * because the recogniser gains and loses a block at the edge of the frame from one pass to
 * the next, and a mean would read that flicker as the hand moving. See [drift] for how a
 * block is matched to itself across frames.
 */
internal class Steadiness(
    private val minChars: Int = 40,
    private val tolerance: Float = 0.02f,
    private val holdFrames: Int = 2,
) {
    private var last: List<Block> = emptyList()
    private var still = 0

    fun next(blocks: List<Block>, chars: Int, diagonal: Float): Framing {
        val before = last
        last = blocks
        if (blocks.isEmpty() || chars < minChars || diagonal <= 0f) {
            still = 0
            return Framing.NONE
        }
        val moved = drift(before, blocks)
        still = if (moved != null && moved <= tolerance * diagonal) still + 1 else 0
        return if (still >= holdFrames) Framing.STEADY else Framing.FOUND
    }
}

/**
 * The median distance each block in [after] has moved since [before]. Null when either is empty.
 *
 * A block is matched to the one in [before] with the same text. Matching on position alone
 * fails on the page this is for: a lease is evenly spaced paragraphs, and a move of one
 * paragraph's height lines every block up with its neighbour and looks like no move at all.
 * Nearest position is the fallback for when no text matches, which a misread letter in
 * every block from one frame to the next can cause.
 */
internal fun drift(before: List<Block>, after: List<Block>): Float? {
    if (before.isEmpty() || after.isEmpty()) return null
    val same = after.mapNotNull { b ->
        if (b.key.isEmpty()) null else before.filter { it.key == b.key }.minOfOrNull { distance(it, b) }
    }
    val moves = same.ifEmpty { after.map { b -> before.minOf { a -> distance(a, b) } } }.sorted()
    return moves[moves.size / 2]
}

/**
 * For each block in [next], the block in [shown] it most likely is, so the outline can glide
 * from there; null when nothing shown is within [reach] and the block has just appeared.
 */
internal fun pair(shown: List<Block>, next: List<Block>, reach: Float): List<Block?> =
    next.map { b -> shown.minByOrNull { distance(it, b) }?.takeIf { distance(it, b) <= reach } }

internal fun lerp(a: Block, b: Block, t: Float): Block = Block(
    left = a.left + (b.left - a.left) * t,
    top = a.top + (b.top - a.top) * t,
    right = a.right + (b.right - a.right) * t,
    bottom = a.bottom + (b.bottom - a.bottom) * t,
)

private fun distance(a: Block, b: Block): Float = hypot(a.centreX - b.centreX, a.centreY - b.centreY)
