package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.TestStoreProduct
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.Offer
import dev.kesav.redline.Plan
import dev.kesav.redline.SavedLease
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The paywall and the scrolled report sections, rendered on the JVM for review with -Pshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class PaywallShotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private fun offer(plan: Plan, type: PackageType, id: String, price: String, micros: Long) = Offer(
        plan = plan,
        pkg = Package(id, type, TestStoreProduct(id, id, id, id, Price(price, micros, "USD")), PresentedOfferingContext("default")),
        price = price,
        trial = null,
    )

    private val offers = listOf(
        offer(Plan.PRO_LIFETIME, PackageType.LIFETIME, "\$rc_lifetime", "$99.99", 99_990_000),
        offer(Plan.PRO_MONTHLY, PackageType.MONTHLY, "\$rc_monthly", "$9.99", 9_990_000),
    )

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent {
            RedlineTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun paywall() {
        screen {
            PaywallSheet(
                state = sample(), offers = offers, reason = PaywallReason.REPORT, busy = false, known = true,
                pitch = null, onBuy = {}, onRestore = {}, onRetry = {}, onDismiss = {},
            )
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/paywall.png")
    }

    @Test
    fun draft() {
        screen { DraftSheet(state = sample(), onDismiss = {}) }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/draft.png")
    }

    @Test
    fun reportSections() {
        screen {
            Results(
                state = sample(), unlocked = false, known = true, busy = false, price = "$99.99", lead = "From $9.99",
                onUnlock = {}, onRestore = {}, onBack = {}, onShare = {}, onLetter = {},
                onShareCount = {}, onChecks = {}, modifier = Modifier.fillMaxSize(),
            )
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(1)
        compose.onRoot().captureRoboImage("build/shots/report-bento.png")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(3)
        compose.onRoot().captureRoboImage("build/shots/report-money.png")
    }

    @Test
    fun compare() {
        val text = File("src/main/assets/sample_lease.txt").readText()
        // A second flat with the same shape of lease but a fair deposit and no exit penalty.
        val milder = """
            RESIDENTIAL TENANCY AGREEMENT
            1. Term. The tenancy runs for twelve months from the start date.
            2. Rent. The rent is payable monthly in advance on the first day of each month.
            3. Deposit. The Tenant shall pay a deposit equal to one month's rent, returned within thirty days of the end of the tenancy.
            4. Repairs. The Landlord shall keep the structure, plumbing and wiring in repair.
            5. Entry. The Landlord may enter the premises on twenty-four hours' written notice at a reasonable time.
            6. Notice. Either party may end the tenancy after the first six months on two months' written notice.
        """.trimIndent()
        val saved = listOf(
            SavedLease("b", "Flat on Park Road", 1L, milder, null),
            SavedLease("a", "Sample lease", 2L, text, null),
        )
        screen { CompareScreen(current = sample(), currentId = "a", saved = saved, onBack = {}, onScanAnother = {}) }
        // The rings and bars wait a beat before they fill, and a delay is not a frame, so
        // waitForIdle alone would photograph them empty.
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/compare.png")
    }
}
