package dev.kesav.redline.ui

import android.app.Application
import android.graphics.Bitmap
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
import org.robolectric.annotation.GraphicsMode

/**
 * The two screens and the share card, rendered on the JVM.
 *
 * Run with `./gradlew :app:testDebugUnitTest -Pshots` and the images land in
 * `app/build/shots/`. Without the flag the captures are skipped and only the layout runs,
 * which still catches a screen that throws while composing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application, because the real one configures RevenueCat, which has no key here.
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private fun screen(content: @Composable () -> Unit) {
        compose.setContent {
            RedlineTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Composable
    private fun Start() = Editor(
        text = "", price = "$4.99", onText = {}, onScan = {}, onSample = {}, onChecks = {},
        reading = null, source = null, photoPages = 0, onOpen = {}, onPhoto = {},
    )

    @Composable
    private fun Report(unlocked: Boolean) = Results(
        state = sample(), unlocked = unlocked, known = true, busy = false, price = "$4.99",
        onUnlock = {}, onRestore = {}, onBack = {}, onShare = {}, onLetter = {},
        onShareCount = {}, onChecks = {},
    )

    @Test
    fun start() {
        screen { Start() }
        shot("start")
    }

    @Test
    @Config(qualifiers = "+night")
    fun startDark() {
        screen { Start() }
        shot("start-dark")
    }

    @Test
    fun reading() {
        // With animations off the pen stands where the page count says it is, which is the
        // one frame that shows the tilt, the read lines and the bar together. With them on,
        // the test clock cancels the endless sweep and the pen never appears.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        android.provider.Settings.Global.putFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f,
        )
        screen {
            Editor(
                text = "", price = "$4.99", onText = {}, onScan = {}, onSample = {}, onChecks = {},
                reading = "Reading page 3 of 5", source = null, photoPages = 0, onOpen = {}, onPhoto = {},
                readingProgress = 0.4f,
            )
        }
        shot("reading")
    }

    @Test
    fun reportLocked() {
        screen { Report(unlocked = false) }
        shot("report-locked")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(2)
        shot("report-locked-index")
    }

    @Test
    fun reportOpen() {
        screen { Report(unlocked = true) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(1)
        shot("report-open")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(4)
        shot("report-open-more")
    }

    @Test
    @Config(qualifiers = "+night")
    fun reportOpenDark() {
        screen { Report(unlocked = true) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(1)
        shot("report-open-dark")
    }

    @Test
    fun reportPdf() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        // Robolectric has no native PDF backend: PdfDocument reports itself closed. The
        // layout is checked on a device; here the test only runs where PDFs can be made.
        val pdf = runCatching { ReportPdf.write(context, sample()) }.getOrNull()
        org.junit.Assume.assumeTrue("PdfDocument unavailable on this runtime", pdf != null)
        assertTrue("empty PDF", pdf!!.length() > 2_000)
        if (System.getProperty("roborazzi.test.record") == "true") {
            File("build/shots").mkdirs()
            pdf.copyTo(File("build/shots/report.pdf"), overwrite = true)
        }
    }

    @Test
    fun shareCard() {
        val card = drawShareCard(sample())
        assertEquals(1080, card.width)
        assertEquals(1350, card.height)
        if (System.getProperty("roborazzi.test.record") == "true") {
            File("build/shots").mkdirs()
            File("build/shots/share-card.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
