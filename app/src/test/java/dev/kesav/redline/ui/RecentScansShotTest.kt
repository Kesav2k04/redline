package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kesav.redline.Place
import dev.kesav.redline.SavedLease
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recent scans block, rendered on the JVM for review with -Pshots: three rows, then all four. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class RecentScansShotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_790_337_600_000L // 25 Sep 2026, 12:00 UTC
    private val day = 24 * 60 * 60 * 1000L
    private val sample = File("src/main/assets/sample_lease.txt").readText()

    // A short lease with nothing in it that costs money, so one row lands in the low tier.
    private val plain = """
        RESIDENTIAL LEASE AGREEMENT

        1. Rent. The Tenant shall pay the rent on the first day of each month.

        2. Repairs. The Landlord shall keep the structure, the roof and the heating in repair.

        3. Ending. Either party may end this agreement by giving one month's notice in writing.
    """.trimIndent()

    private val saved = listOf(
        SavedLease("a", "Sample lease", now - 2 * 60 * 60 * 1000L, sample, null),
        SavedLease("b", "Maple Court, flat 12", now - day, sample.substringBefore("5. "), Place.ENGLAND),
        SavedLease("c", "Harbour Street room", now - 3 * day, plain, null),
        SavedLease("d", "Elm Road, flat 3", now - 20 * day, sample, Place.INDIA),
    )

    private fun block() {
        compose.setContent {
            RedlineTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        RecentScans(saved = saved, onOpen = {}, onForget = {}, now = now)
                    }
                }
            }
        }
        // Each row is scanned again off the main thread; wait for the scores to land.
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("Risk", substring = true)).fetchSemanticsNodes().size >= 3
        }
        compose.waitForIdle()
    }

    @Test
    fun threeRows() {
        block()
        captureScreenRoboImage("build/shots/recent.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun allRowsDark() {
        block()
        compose.onNodeWithText("Show all 4").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("Risk", substring = true)).fetchSemanticsNodes().size >= 4
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/recent-all-dark.png")
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
}
