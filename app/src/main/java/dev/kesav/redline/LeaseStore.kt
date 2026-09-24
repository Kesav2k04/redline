package dev.kesav.redline

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One lease the reader scanned, kept on the phone so two can be compared later. */
data class SavedLease(
    val id: String,
    val title: String,
    val savedAt: Long,
    val text: String,
    val place: Place?,
)

/**
 * The leases scanned on this phone, newest first, in one JSON file in the app's private storage.
 *
 * Nothing here leaves the phone and nothing is kept that the reader did not scan. It holds the
 * text rather than the result, because the rules improve between versions and a comparison
 * should use today's rules on both leases, not last month's on one of them. Twelve is plenty for
 * a flat hunt and small enough that the file is read in one go.
 */
object LeaseStore {
    private const val FILE = "leases.json"
    private const val KEEP = 12

    fun all(context: Context): List<SavedLease> = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SavedLease(
                id = o.getString("id"),
                title = o.getString("title"),
                savedAt = o.getLong("savedAt"),
                text = o.getString("text"),
                place = Place.fromName(o.optString("place").takeIf { it.isNotBlank() }),
            )
        }
    }.getOrDefault(emptyList())

    /** Saves [text] under its fingerprint, so the same lease scanned twice is one entry. */
    fun save(context: Context, text: String, title: String, place: Place?): List<SavedLease> {
        val id = leaseFingerprint(text)
        val lease = SavedLease(id, title, System.currentTimeMillis(), text, place)
        val next = (listOf(lease) + all(context).filter { it.id != id }).take(KEEP)
        write(context, next)
        return next
    }

    fun remove(context: Context, id: String): List<SavedLease> {
        val next = all(context).filter { it.id != id }
        write(context, next)
        return next
    }

    private fun write(context: Context, leases: List<SavedLease>) {
        val array = JSONArray()
        for (l in leases) {
            array.put(
                JSONObject()
                    .put("id", l.id)
                    .put("title", l.title)
                    .put("savedAt", l.savedAt)
                    .put("text", l.text)
                    .put("place", l.place?.name ?: "")
            )
        }
        val file = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }

    /**
     * A name for a lease the reader will recognise in a list: the file it came from, else its own
     * first line when that reads like a title, else the address or party line, else a date.
     */
    fun titleFor(text: String, source: String?): String {
        source?.substringAfter("Read from ")?.substringBefore(",")?.substringBefore(" on this phone")
            ?.takeIf { it.isNotBlank() && !it.startsWith("the ") && !it.contains("photographed") }
            ?.let { return it.removeSuffix(".pdf").removeSuffix(".PDF").take(40) }
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.length in 4..60 }
        return first?.take(40) ?: "Lease"
    }
}

/** A saved lease scanned again with today's rules. */
internal fun SavedLease.scan(): ScanState.Scanned {
    val clauses = ClauseSplitter.split(text)
    val findings = Scanner.ranked(clauses, place)
    return ScanState.Scanned(clauses.size, findings, LeaseCheck.looksLikeLease(text), place, clauses)
}
