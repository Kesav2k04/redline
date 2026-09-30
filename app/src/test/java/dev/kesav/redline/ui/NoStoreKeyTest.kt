package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A clone built without a store key never gets offers. The paywall has to say that once and
 * stop, not spin on "Reaching the store" for someone who will never see prices arrive.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class NoStoreKeyTest {

    @get:Rule
    val compose = createComposeRule()

    private val state: ScanState.Scanned by lazy {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private var retries = 0

    private fun paywall(storeKey: Boolean) = compose.setContent {
        RedlineTheme {
            Surface {
                PaywallSheet(
                    state = state, offers = emptyList(), reason = PaywallReason.REPORT, busy = false, known = true,
                    pitch = null, onBuy = {}, onRestore = {}, onRetry = { retries++ }, onDismiss = {},
                    storeKey = storeKey,
                )
            }
        }
    }

    @Test
    fun `a build with no store key says purchases are off and where they are on`() {
        assertEquals(
            "This build has no store key, so purchases are off. The latest Release APK on GitHub has them.",
            noOffersLine(storeKey = false),
        )
    }

    @Test
    fun `a build with a store key keeps the loading line while the offering arrives`() {
        assertEquals("Reaching the store for prices. Your scan is kept.", noOffersLine(storeKey = true))
    }

    @Test
    fun `without a key the sheet shows the final line and never asks the store again`() {
        paywall(storeKey = false)
        compose.waitForIdle()
        compose.onNodeWithText(noOffersLine(storeKey = false)).assertExists()
        compose.onNodeWithText("Reaching the store", substring = true).assertDoesNotExist()
        assertEquals(0, retries)
    }

    @Test
    fun `with a key the sheet keeps the loading line and asks the store`() {
        paywall(storeKey = true)
        compose.waitForIdle()
        compose.onNodeWithText(noOffersLine(storeKey = true)).assertExists()
        assertTrue(retries >= 1)
        assertFalse(noOffersLine(storeKey = true).contains("no store key"))
    }
}
