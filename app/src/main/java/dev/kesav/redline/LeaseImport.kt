package dev.kesav.redline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a lease out of a PDF or a photo, on the phone.
 *
 * Leases arrive as a PDF the landlord emailed or as paper, and asking a tenant to copy
 * text out of either was where people gave up. Both paths stay local, which is the
 * promise on the first screen: the PDF text layer is read directly where Android can
 * read it (API 35 and up), and everything else goes through ML Kit's bundled text
 * recognition model, which ships inside the APK and never downloads or uploads anything.
 * That is why it is the bundled model and not the smaller Play services one: the other
 * fetches its model over the network on first use, which would break the airplane-mode
 * claim the first time anyone tried it.
 */
object LeaseImport {

    /** Enough for any residential lease; a hundred-page document is not one. */
    const val MAX_PAGES = 30

    // Long edge of the image handed to the recogniser. A4 at this size puts ten-point
    // body text at about 28 pixels a line, comfortably above the 16 pixels per character
    // ML Kit asks for, while one page stays near 14 MB of bitmap rather than 50.
    private const val LONG_EDGE = 2400

    // Below this many letters a PDF page is treated as scanned: its text layer is empty
    // or holds only a page number, and the pixels are the real content.
    private const val MIN_LAYER_LETTERS = 40

    sealed interface Result {
        data class Read(
            val text: String,
            val name: String?,
            val kind: Kind,
            val pages: Int,
            val totalPages: Int,
            /**
             * True when any of the text came from recognising pixels rather than from a
             * text layer. Recognised text can misread a "10", so it goes to the editor to
             * be checked; a PDF's own text cannot, so it goes straight to the scan.
             */
            val recognised: Boolean = false,
        ) : Result

        data class Failed(val message: String) : Result
    }

    enum class Kind { PDF, PHOTO, TEXT }

    suspend fun read(
        context: Context,
        uri: Uri,
        progress: (step: String, done: Float?) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(context, uri)
        val type = resolver.getType(uri) ?: typeFromName(name)

        try {
            when {
                type == "application/pdf" -> readPdf(context, uri, name, progress)
                type?.startsWith("image/") == true -> readPhoto(context, uri, name, progress)
                type?.startsWith("text/") == true -> readText(context, uri, name)
                else -> Result.Failed(
                    "Redline reads PDFs and photos. For a Word file, open it, copy the text and paste it here."
                )
            }
        } catch (e: SecurityException) {
            // PdfRenderer's answer to a password, and also what a revoked share grant
            // looks like. Both have the same way out.
            Result.Failed("That file is locked. Open it in its own app, copy the text and paste it here.")
        } catch (e: IOException) {
            Result.Failed("That file could not be opened.")
        } catch (e: OutOfMemoryError) {
            Result.Failed("That file is too large to read on this phone. Try a photo of each page instead.")
        }
    }

    private suspend fun readPdf(
        context: Context,
        uri: Uri,
        name: String?,
        progress: (String, Float?) -> Unit,
    ): Result {
        // PdfRenderer needs a seekable descriptor, and a mail attachment is often a pipe.
        // Copying to the cache is the one approach that works for every provider, and
        // the copy is deleted before this returns, so no lease is left on disk.
        val copy = File.createTempFile("lease", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("no stream")

            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                val recogniser = lazy { recogniser() }
                try {
                    val total = renderer.pageCount
                    val count = minOf(total, MAX_PAGES)
                    val pages = mutableListOf<List<String>>()
                    var recognised = false

                    for (i in 0 until count) {
                        // A page read from its text layer never suspends, so Cancel is let in here.
                        currentCoroutineContext().ensureActive()
                        // The share of pages already read, once there is more than one to count.
                        progress("Reading page ${i + 1} of $count", if (count > 1) i.toFloat() / count else null)
                        val page = renderer.openPage(i)
                        try {
                            val layer = textLayer(page)
                            pages += if (layer.count(Char::isLetter) >= MIN_LAYER_LETTERS) {
                                listOf(layer)
                            } else {
                                recognised = true
                                recognise(recogniser.value, InputImage.fromBitmap(render(page), 0))
                            }
                        } finally {
                            page.close()
                        }
                    }

                    val text = PageText.assemble(pages)
                    return if (text.isBlank()) {
                        Result.Failed("No text found in that PDF.")
                    } else {
                        Result.Read(text, name, Kind.PDF, count, total, recognised)
                    }
                } finally {
                    if (recogniser.isInitialized()) recogniser.value.close()
                    renderer.close()
                }
            }
        } finally {
            copy.delete()
        }
    }

    private fun textLayer(page: PdfRenderer.Page): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            page.textContents.joinToString("\n") { it.text }
        } else {
            ""
        }

    private fun render(page: PdfRenderer.Page): Bitmap {
        val scale = LONG_EDGE.toFloat() / maxOf(page.width, page.height)
        val bitmap = Bitmap.createBitmap(
            (page.width * scale).toInt(),
            (page.height * scale).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        // Pages render onto transparency, and transparent pixels read as black to the
        // recogniser: black text on black.
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        return bitmap
    }

    private suspend fun readPhoto(
        context: Context,
        uri: Uri,
        name: String?,
        progress: (String, Float?) -> Unit,
    ): Result {
        progress("Reading the photo", null)
        val resolver = context.contentResolver

        // Decoded at a fraction of full size: a phone photo is 12 to 50 megapixels, and
        // a page of text needs about five.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return Result.Failed("That image could not be opened.")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) }
        }.getOrNull() ?: 0

        val recogniser = recogniser()
        val blocks = try {
            recognise(recogniser, InputImage.fromBitmap(bitmap, rotation))
        } finally {
            recogniser.close()
        }

        val text = PageText.assemble(listOf(blocks))
        return if (text.isBlank()) {
            Result.Failed("No text found. Try again with the page flat and in good light.")
        } else {
            Result.Read(text, name, Kind.PHOTO, 1, 1, recognised = true)
        }
    }

    private fun readText(context: Context, uri: Uri, name: String?): Result {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        }.orEmpty()
        return if (text.isBlank()) {
            Result.Failed("That file is empty.")
        } else {
            Result.Read(text, name, Kind.TEXT, 1, 1)
        }
    }

    private fun recogniser(): TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private suspend fun recognise(recogniser: TextRecognizer, image: InputImage): List<String> =
        recogniser.process(image).await().textBlocks.map { block ->
            block.lines.joinToString("\n") { it.text }
        }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun typeFromName(name: String?): String? = when (name?.substringAfterLast('.')?.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg", "png", "webp", "heic", "heif" -> "image/*"
        "txt" -> "text/plain"
        else -> null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
