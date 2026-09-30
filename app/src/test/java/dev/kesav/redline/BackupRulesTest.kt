package dev.kesav.redline

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The paywall says "Lease text stays on this phone" and the share card says the lease was never
 * uploaded. Android copies an app's files to the owner's Drive by default, and to a new phone on
 * transfer, so the saved leases have to be left out by name in both rules files: Android 12 and
 * later read one, Android 11 and earlier the other.
 */
class BackupRulesTest {

    private val leases = setOf("leases.json", "leases.json.tmp")

    private fun xml(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))
        .documentElement

    /** The paths [section] leaves out of the app's files directory. */
    private fun excluded(section: Element): Set<String> {
        val nodes = section.getElementsByTagName("exclude")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("domain") == "file" }
            .map { it.getAttribute("path") }
            .toSet()
    }

    @Test
    fun `the manifest names both rules files`() {
        val app = xml("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
    }

    @Test
    fun `android 12 and later leave the leases out of cloud backup and device transfer`() {
        val rules = xml("src/main/res/xml/data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) {
            val section = rules.getElementsByTagName(name).item(0) as Element
            assertTrue("$name copies the saved leases", excluded(section).containsAll(leases))
        }
    }

    @Test
    fun `android 11 and earlier leave the leases out of backup`() {
        assertTrue(excluded(xml("src/main/res/xml/backup_rules.xml")).containsAll(leases))
    }
}
