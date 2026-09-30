package dev.kesav.redline.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on the report, checked on the semantics tree rather than by
 * listening. Each assertion is a sentence TalkBack speaks, so a refactor that drops one
 * fails here instead of in front of someone who cannot see the screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class SemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val state: ScanState.Scanned by lazy {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    private fun report(unlocked: Boolean) = compose.setContent {
        RedlineTheme {
            Surface {
                Results(
                    state = state, unlocked = unlocked, known = true, busy = false, price = "$99.99", lead = "From $9.99",
                    onUnlock = {}, onRestore = {}, onBack = {}, onShare = {}, onLetter = {},
                    onShareCount = {}, onChecks = {},
                )
            }
        }
    }

    private fun scrollTo(index: Int) =
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(index)

    @Test
    fun `the locked index is one spoken sentence naming what is locked, and says what a tap does`() {
        report(unlocked = false)
        val locked = state.groups.size - 1
        val index = hasContentDescription("$locked more clauses, locked", substring = true)
        // The index sits below the report's sections, so scroll by what it says, not where it is.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(index)
        compose.onNode(index).assertHasClickAction()
        // Every subject is in that sentence, so nothing on screen is withheld from the ear.
        val first = state.groups.drop(1).first().findings.first().topic
        compose.onNode(hasContentDescription(first, substring = true)).assertHasClickAction()
        // The rows show severity by colour only, so the sentence has to say it.
        compose.onNode(hasContentDescription(", serious", substring = true)).assertHasClickAction()
    }

    @Test
    fun `an open clause card offers See it in the lease as an action`() {
        var opened: Int? = null
        compose.setContent {
            RedlineTheme {
                Surface {
                    Results(
                        state = state, unlocked = true, known = true, busy = false, price = "$99.99", lead = "From $9.99",
                        onUnlock = {}, onRestore = {}, onBack = {}, onShare = {}, onLetter = {},
                        onShareCount = {}, onChecks = {}, onDocument = { opened = it },
                    )
                }
            }
        }
        // The card speaks as one node, so the button inside it has to be an action on the card.
        val offers = SemanticsMatcher("offers See it in the lease") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "See it in the lease" }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(offers)
        val card = compose.onAllNodes(offers).onFirst().fetchSemanticsNode()
        compose.runOnIdle {
            card.config[SemanticsActions.CustomActions].first { it.label == "See it in the lease" }.action()
        }
        // Only the first card opens by itself, so it is the one that answers.
        assertEquals(state.groups.first().clause.index, opened)
    }

    @Test
    fun `the header count is spoken as a sentence, and only once the summary has scrolled away`() {
        report(unlocked = true)
        val spoken = "${state.flaggedClauses} of ${state.clauseCount} clauses flagged"
        compose.onAllNodesWithContentDescription(spoken).assertCountEquals(0)
        scrollTo(3)
        compose.onNodeWithContentDescription(spoken).assertExists()
    }
}
