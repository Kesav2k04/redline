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
        offer(Plan.PASS, PackageType.CUSTOM, "lease_pass", "$1.99", 1_990_000),
        offer(Plan.PRO_LIFETIME, PackageType.LIFETIME, "\$rc_lifetime", "$4.99", 4_990_000),
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
    fun reportSections() {
        screen {
            Results(
                state = sample(), unlocked = false, known = true, busy = false, price = "$1.99",
                onUnlock = {}, onRestore = {}, onBack = {}, onShare = {}, onLetter = {},
                onShareCount = {}, onChecks = {}, modifier = Modifier.fillMaxSize(),
            )
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(1)
        compose.onRoot().captureRoboImage("build/shots/report-bento.png")
    }
}
