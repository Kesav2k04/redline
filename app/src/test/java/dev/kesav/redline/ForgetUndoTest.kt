package dev.kesav.redline

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Forgetting a saved lease from the start screen can be undone while the view model holds it,
 * and sticks once the hold runs out. The timer runs on the main looper, so the test moves that
 * clock by hand rather than waiting five real seconds.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ForgetUndoTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun lease(title: String, savedAt: Long, text: String) =
        SavedLease(leaseFingerprint(text), title, savedAt, text, null)

    // Fixed dates, so the undo can be held to the exact title, date and place in the list.
    private val harbour = lease(
        "Harbour Street room", 2_000L,
        "1. Rent. The Tenant shall pay the rent on the first day of each month.",
    )
    private val maple = lease(
        "Maple Court, flat 12", 1_000L,
        "1. Deposit. The Tenant shall pay a security deposit equal to ten months rent.",
    )

    @Before
    fun seed() {
        File(app.filesDir, "leases.json").delete()
        LeaseStore.restore(app, maple)
        LeaseStore.restore(app, harbour)
    }

    /** Runs the main looper until [check] holds; the file work in between is on real threads. */
    private fun until(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!check()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun loaded(): ScanViewModel {
        // Both seeds on disk: a store that kept only its first write would fail here, not later.
        assertEquals(listOf(harbour, maple), LeaseStore.all(app))
        val vm = ScanViewModel(app, SavedStateHandle())
        until("the saved leases") { vm.ui.value.saved.size == 2 }
        return vm
    }

    @Test
    fun undoPutsTheLeaseBackAsItWas() {
        val vm = loaded()

        vm.forget(maple.id)
        until("the hold") { vm.ui.value.forgotten != null }
        assertEquals(maple, vm.ui.value.forgotten)
        assertEquals(listOf(harbour), vm.ui.value.saved)
        // Gone from the file at once, not only from the screen.
        assertEquals(listOf(harbour), LeaseStore.all(app))

        vm.undoForget()
        until("the restore") { vm.ui.value.saved.size == 2 }
        assertNull(vm.ui.value.forgotten)
        // Its own title and date, and so its old place under the newer lease.
        assertEquals(listOf(harbour, maple), vm.ui.value.saved)
        assertEquals(listOf(harbour, maple), LeaseStore.all(app))
    }

    @Test
    fun withoutUndoTheLeaseStaysGone() {
        val vm = loaded()
        val looper = shadowOf(Looper.getMainLooper())

        vm.forget(maple.id)
        until("the hold") { vm.ui.value.forgotten != null }

        looper.idleFor(Duration.ofMillis(UNDO_HOLD_MILLIS - 1))
        assertNotNull("Still held just before the hold ends", vm.ui.value.forgotten)
        looper.idleFor(Duration.ofMillis(1))
        assertNull("Released once the hold ends", vm.ui.value.forgotten)

        // Too late: there is nothing held to put back.
        vm.undoForget()
        looper.idle()
        assertEquals(listOf(harbour), vm.ui.value.saved)
        assertEquals(listOf(harbour), LeaseStore.all(app))
    }
}
