package dev.blaze.controller

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPermissionsTest {

    private fun mergedManifest(): File {
        val root = File(System.getProperty("mergedManifestRoot"))
        return root.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && it.path.contains("merged_manifest") && it.path.contains("debug") }
            .firstOrNull()
            ?: error("merged debug manifest not found under $root")
    }

    @Test
    fun requestsOnlyBluetoothAndMicrophonePermissions() {
        val xml = mergedManifest().readText()
        val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(xml).map { it.groupValues[1] }.toSet()
        // DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is injected by AndroidX for the app's own package; it is not a capability.
        val withoutInternalOnes = permissions.filterNot { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }.toSet()
        assertEquals(
            setOf(
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.RECORD_AUDIO", // tuner; audio is processed on the phone and the app has no INTERNET permission
            ),
            withoutInternalOnes,
        )
        assertTrue("INTERNET must never be requested", "android.permission.INTERNET" !in permissions)
    }

    @Test
    fun scanPermissionDeclaresNeverForLocation() {
        val xml = mergedManifest().readText()
        assertTrue(
            Regex("<uses-permission[^>]*BLUETOOTH_SCAN[^>]*neverForLocation").containsMatchIn(xml),
        )
    }
}
