package dev.kesav.redline.ui.document

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kesav.redline.ClauseGroup
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import dev.kesav.redline.ui.RedlineTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lease viewer, rendered on the JVM for review with -Pshots: the top, an open finding, a locked one. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class DocumentViewerShotTest {

    @get:Rule
    val compose = createComposeRule()

    private val state: ScanState.Scanned by lazy {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses), clauses = clauses)
    }

    // The free report: the worst clause readable, every other flag locked.
    private val locked: Set<Int> by lazy { state.groups.drop(1).map { it.clause.index }.toSet() }

    private fun viewer(focus: Int?) {
        compose.setContent {
            RedlineTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    DocumentViewer(state.clauses, state.groups, focus, locked, onBack = {})
                }
            }
        }
    }

    /** A few words from the middle of the clause, which is on screen once the viewer opens at it. */
    private fun tap(group: ClauseGroup) {
        val words = group.clause.text.split(' ').filter { it.isNotBlank() }
        val phrase = words.drop(words.size / 2).take(3).joinToString(" ")
        compose.onAllNodesWithText(phrase, substring = true).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun top() {
        viewer(focus = null)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/document.png")
    }

    @Test
    fun openFinding() {
        val first = state.groups.first()
        viewer(focus = first.clause.index)
        compose.waitForIdle()
        tap(first)
        captureScreenRoboImage("build/shots/document-open.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun lockedFindingDark() {
        val second = state.groups[1]
        viewer(focus = second.clause.index)
        compose.waitForIdle()
        tap(second)
        captureScreenRoboImage("build/shots/document-locked-dark.png")
    }
}
