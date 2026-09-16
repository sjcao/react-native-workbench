package dev.gaphunter.reactnativecompanion.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdbExecutableTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun executable(sdk: File): File = File(sdk, "platform-tools/adb").apply {
        parentFile.mkdirs()
        createNewFile()
    }

    @Test fun macWithoutSdkEnvironmentUsesDefaultInstallation() {
        val adb = executable(File(temporary.root, "Library/Android/sdk"))
        assertEquals(adb.absolutePath,
            ReactNativeCommandRunner.adbExecutable(emptyMap(), temporary.root.path, windows = false, mac = true))
    }

    @Test fun explicitSdkTakesPriorityOverDefault() {
        executable(File(temporary.root, "Library/Android/sdk"))
        val sdk = temporary.newFolder("custom-sdk")
        val adb = executable(sdk)
        assertEquals(adb.absolutePath,
            ReactNativeCommandRunner.adbExecutable(mapOf("ANDROID_HOME" to sdk.path),
                temporary.root.path, windows = false, mac = true))
    }

    @Test fun missingSdkRetainsPathLookup() {
        assertEquals("adb",
            ReactNativeCommandRunner.adbExecutable(emptyMap(), temporary.root.path, windows = false, mac = true))
    }

    @Test fun xcdeviceProbeHasItsOwnBoundedTimeout() {
        assertTrue(ReactNativeCommandRunner.runXcdeviceList().parametersList.parameters.contains("--timeout=5"))
    }
}
