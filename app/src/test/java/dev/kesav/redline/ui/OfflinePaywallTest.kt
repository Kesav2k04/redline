package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * With no connection the paywall used to show a spinner for good: no reason and nothing to press.
 * After about ten seconds it now says what is wrong and offers to try again, and a store that
 * answered with nothing to sell says that instead of blaming the connection.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class OfflinePaywallTest {

    @get:Rule
    val compose = createComposeRule()

    private val state: ScanState.Scanned by lazy {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private var retries = 0

    private fun paywall(reached: Boolean = false) = compose.setContent {
        RedlineTheme {
            Surface {
                PaywallSheet(
                    state = state, offers = emptyList(), reason = PaywallReason.REPORT, busy = false, known = true,
                    pitch = null, onBuy = {}, onRestore = {}, onRetry = { retries++ }, onDismiss = {},
                    storeKey = true, storeReached = reached,
                )
            }
        }
    }

    @Test
    fun `each wait has its own words`() {
        assertEquals("Reaching the store for prices. Your scan is kept.", noOffersLine(storeKey = true))
        assertEquals(
            "No connection. Prices need the internet. Your scan is kept.",
            noOffersLine(storeKey = true, waited = true),
        )
        assertEquals("Purchases are not available right now.", noOffersLine(storeKey = true, reached = true, waited = true))
        // A build with no key says so whatever else is true.
        assertEquals(noOffersLine(storeKey = false), noOffersLine(storeKey = false, reached = true, waited = true))
    }

    @Test
    fun `after the wait the sheet says there is no connection and asks again on a tap`() {
        paywall()
        compose.waitForIdle()
        compose.onNodeWithText("Reaching the store", substring = true).assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(OFFLINE_AFTER_MILLIS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithText("No connection. Prices need the internet. Your scan is kept.").assertExists()

        val before = retries
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        assertTrue("Try again asks the store", retries > before)
        // Asking again starts the wait over rather than repeating the failure at once.
        compose.onNodeWithText("Reaching the store", substring = true).assertExists()
    }

    @Test
    fun `a store with nothing to sell says so at once, with a way to ask again`() {
        paywall(reached = true)
        compose.waitForIdle()
        compose.onNodeWithText("Purchases are not available right now.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }
}
