package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.TestStoreProduct
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.Offer
import dev.kesav.redline.Plan
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A failed purchase puts a line above the buy button. On a phone the button was already near the
 * bottom of the sheet, so the line pushed it out of sight; the sheet now scrolls it back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class BuyInViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val state: ScanState.Scanned by lazy {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private val lifetime = Offer(
        plan = Plan.PRO_LIFETIME,
        pkg = Package(
            "\$rc_lifetime", PackageType.LIFETIME,
            TestStoreProduct("lifetime", "lifetime", "lifetime", "lifetime", Price("$4.99", 4_990_000, "USD")),
            PresentedOfferingContext("default"),
        ),
        price = "$4.99",
        trial = null,
    )

    @Test
    fun `a message above the buy button leaves the button on screen`() {
        val message = mutableStateOf<String?>(null)
        compose.setContent {
            RedlineTheme {
                Surface {
                    PaywallSheet(
                        state = state, offers = listOf(lifetime), reason = PaywallReason.REPORT, busy = false, known = true,
                        pitch = null, onBuy = {}, onRestore = {}, onRetry = {}, onDismiss = {}, message = message.value,
                    )
                }
            }
        }
        compose.waitForIdle()
        // The window is small enough that the button starts below the fold.
        compose.onNodeWithText("Get Renter Pro for $4.99").assertIsNotDisplayed()

        compose.runOnIdle { message.value = "The purchase did not go through. You were not charged." }
        compose.waitForIdle()
        compose.onNodeWithText("Get Renter Pro for $4.99").assertIsDisplayed()
    }
}
