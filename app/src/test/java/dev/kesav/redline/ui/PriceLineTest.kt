package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.kesav.redline.PriceLead
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The start screen states the price to a reader who has not paid, and not to one who has. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class PriceLineTest {

    @get:Rule
    val compose = createComposeRule()

    private fun start(pro: Boolean) = compose.setContent {
        RedlineTheme {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Editor(
                    text = "", price = PriceLead("$1.99", from = true, once = false, per = "month"), onText = {}, onScan = {},
                    onSample = {}, onChecks = {}, reading = null, source = null, photoPages = 0, onOpen = {}, onPhoto = {},
                    pro = pro,
                )
            }
        }
    }

    @Test
    fun `a reader without Renter Pro sees the price`() {
        start(pro = false)
        compose.onNodeWithText("The full report is", substring = true).assertExists()
    }

    @Test
    fun `a Renter Pro owner is not quoted a price`() {
        start(pro = true)
        compose.onNodeWithText("The full report is", substring = true).assertDoesNotExist()
        compose.onNodeWithText("unlocks with a plan", substring = true).assertDoesNotExist()
    }
}
