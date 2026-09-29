package dev.kesav.redline

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Start over and Back used to throw the lease text away with no way back, after what may have
 * been a long read of a PDF. They now hold it the way a forgotten lease is held: one tap puts it
 * back while the hold lasts, and never over something new in the field.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ClearUndoTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private val lease = "1. Deposit. The Tenant shall pay a security deposit equal to ten months rent."

    private fun filled(): ScanViewModel {
        val vm = ScanViewModel(app, SavedStateHandle())
        vm.edit(lease)
        vm.setRent(2_000)
        return vm
    }

    @Test
    fun undoPutsTheTextBack() {
        val vm = filled()

        vm.clear()
        assertEquals("", vm.ui.value.text)
        assertNotNull(vm.ui.value.cleared)

        vm.undoClear()
        assertEquals(lease, vm.ui.value.text)
        assertEquals(2_000L, vm.ui.value.rent)
        assertNull(vm.ui.value.cleared)
    }

    @Test
    fun withoutUndoTheTextStaysGone() {
        val vm = filled()
        val looper = shadowOf(Looper.getMainLooper())

        vm.clear()
        looper.idleFor(Duration.ofMillis(UNDO_HOLD_MILLIS - 1))
        assertNotNull("Still held just before the hold ends", vm.ui.value.cleared)
        looper.idleFor(Duration.ofMillis(1))
        assertNull("Released once the hold ends", vm.ui.value.cleared)

        vm.undoClear()
        assertEquals("", vm.ui.value.text)
    }

    @Test
    fun undoNeverWritesOverNewText() {
        val vm = filled()

        vm.clear()
        vm.edit("A different lease, pasted after the clear.")
        vm.undoClear()
        assertEquals("A different lease, pasted after the clear.", vm.ui.value.text)
        assertNull(vm.ui.value.cleared)
    }

    @Test
    fun anEmptyFieldHoldsNothing() {
        val vm = ScanViewModel(app, SavedStateHandle())
        vm.clear()
        assertNull(vm.ui.value.cleared)
    }
}
