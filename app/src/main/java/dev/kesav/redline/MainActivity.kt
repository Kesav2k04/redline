package dev.kesav.redline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kesav.redline.ui.RedlineTheme
import dev.kesav.redline.ui.ScanScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val shared = sharedText(intent)

        setContent {
            RedlineTheme {
                ScanScreen(sharedText = shared)
            }
        }
    }

    private fun sharedText(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        else -> null
    }.orEmpty()
}
