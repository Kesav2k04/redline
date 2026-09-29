package dev.kesav.redline

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Reading a PDF or photos had no Cancel, and Back closed the app and lost the import. Cancel now
 * stops the read and returns at once, and nothing the stopped read found lands in the field. The
 * read runs on a real thread, so the test runs the main looper by hand until it has wound up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ImportCancelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    // Whatever this reads as, a refusal or text, it is something that would land.
    private val file: Uri by lazy {
        Uri.fromFile(File(app.cacheDir, "lease").apply { writeText("1. Rent. The Tenant shall pay the rent monthly.") })
    }

    private fun until(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!check()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun landed(vm: ScanViewModel) = vm.ui.value.message != null || vm.ui.value.text.isNotEmpty()

    @Test
    fun withoutCancelTheReadLands() {
        val vm = ScanViewModel(app, SavedStateHandle())
        var finished = false
        vm.import(file) { finished = true }
        until("the read") { finished }
        assertNull(vm.ui.value.reading)
        assertTrue("the read reported nothing", landed(vm))
    }

    @Test
    fun cancelReturnsAtOnceAndNothingLands() {
        val vm = ScanViewModel(app, SavedStateHandle())
        var finished = false
        vm.import(file) { finished = true }
        assertNotNull("the read has started", vm.ui.value.reading)

        vm.cancelImport()
        assertNull("back at once", vm.ui.value.reading)

        // The stopped read still winds up on its own thread; what it found must not land.
        until("the stopped read to wind up") { finished }
        assertNull(vm.ui.value.reading)
        assertNull(vm.ui.value.message)
        assertEquals("", vm.ui.value.text)
    }

    @Test
    fun aFileCanBeReadAgainAfterACancel() {
        val vm = ScanViewModel(app, SavedStateHandle())
        vm.import(file)
        vm.cancelImport()

        var finished = false
        vm.import(file) { finished = true }
        until("the second read") { finished }
        assertNull(vm.ui.value.reading)
        assertTrue("the second read reported nothing", landed(vm))
    }
}
