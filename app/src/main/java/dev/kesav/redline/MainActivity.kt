package dev.kesav.redline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import dev.kesav.redline.ui.RedlineTheme
import dev.kesav.redline.ui.ScanScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val shared = sharedText(intent)
        val file = sharedFile(intent)

        setContent {
            RedlineTheme {
                ScanScreen(sharedText = shared, sharedFile = file)
            }
        }
    }

    /**
     * A PDF or photo handed over by another app: shared from a mail attachment, or opened
     * with Redline from a file manager. A shared .txt file arrives here too, as a stream
     * with no EXTRA_TEXT, which the text path above would have read as nothing.
     */
    private fun sharedFile(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND ->
            if (intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                null
            }
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }

    private fun sharedText(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        else -> null
    }.orEmpty()
}
