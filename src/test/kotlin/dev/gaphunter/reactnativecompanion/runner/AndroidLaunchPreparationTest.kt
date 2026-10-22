package dev.gaphunter.reactnativecompanion.runner

import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidLaunchPreparationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun output(root: File, path: String, variant: String, id: String, apk: Boolean = true) {
        val directory = File(root, path).apply { mkdirs() }
        File(directory, "output-metadata.json").writeText(
            """{"variantName":"$variant","applicationId":"$id","elements":[{"outputFile":"app.apk"}]}"""
        )
        if (apk) File(directory, "app.apk").writeText("fixture")
    }

    @Test fun selectsExactVariantAndUsesFinalApplicationId() {
        output(temporary.root, "debug", "debug", "com.example.app.dev")
        output(temporary.root, "release", "release", "com.example.app")
        output(temporary.root, "demo/debug", "demoDebug", "com.example.demo.dev")
        assertEquals("com.example.app.dev", AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "debug"))
        assertEquals("com.example.demo.dev", AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "DemoDebug"))
        assertEquals("com.example.app", AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "release"))
    }

    @Test fun neverGuessesWhenVariantIsMissingAmbiguousOrApkWasRemoved() {
        output(temporary.root, "release", "release", "com.example.app")
        assertThrows(IllegalArgumentException::class.java) {
            AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "debug")
        }
        output(temporary.root, "stale", "debug", "com.example.old", apk = false)
        assertThrows(IllegalArgumentException::class.java) {
            AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "debug")
        }
        output(temporary.root, "a", "debug", "com.example.one")
        output(temporary.root, "b", "debug", "com.example.two")
        assertThrows(IllegalArgumentException::class.java) {
            AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "debug")
        }
    }

    @Test fun splitApksWithTheSameApplicationIdAreSupported() {
        output(temporary.root, "arm64", "debug", "com.example.app.dev")
        output(temporary.root, "x86", "debug", "com.example.app.dev")
        assertEquals("com.example.app.dev", AndroidLaunchPreparation.applicationIdFromOutputs(temporary.root, "debug"))
    }

    private fun config(source: File): String = JsonObject().apply {
        add("project", JsonObject().apply {
            add("android", JsonObject().apply {
                addProperty("sourceDir", source.absolutePath)
                addProperty("appName", "mobile")
            })
        })
    }.toString()

    @Test fun buildsConfiguredModuleWithEnvironmentBeforeReadingMetadata() {
        val source = temporary.newFolder("native android")
        File(source, if (SystemInfo.isWindows) "gradlew.bat" else "gradlew").writeText("")
        val calls = mutableListOf<GeneralCommandLine>()
        val command = AndroidLaunchPreparation.prepare(
            temporary.root.path, AndroidRunSettings("demoDebug"), DeviceTarget("serial", "phone", DeviceKind.ANDROID), ".env.demo",
        ) { line, visible ->
            calls.add(line)
            assertEquals(".env.demo", line.environment["ENVFILE"])
            when {
                "--help" in line.parametersList.parameters -> "--mode <string>\n--device <string>\n--appId <string>"
                "config" in line.parametersList.parameters -> config(source)
                else -> {
                    assertTrue(visible)
                    assertEquals(source.absolutePath, line.workDirectory!!.absolutePath)
                    assertEquals(":mobile:assembleDemoDebug", line.parametersList.parameters.first())
                    output(File(source, "mobile/build/outputs/apk"), "demo/debug", "demoDebug", "com.example.demo.dev")
                    "BUILD SUCCESSFUL"
                }
            }
        }
        assertEquals(3, calls.size)
        assertEquals(listOf("run-android", "--mode", "demoDebug", "--appId", "com.example.demo.dev", "--device", "serial"), command)
    }

    @Test fun explicitIdSkipsDiscoveryAndUsesLegacyCliOptionsWithoutAppendingSuffix() {
        var calls = 0
        val command = AndroidLaunchPreparation.prepare(
            temporary.root.path, AndroidRunSettings("prodDebug", "com.example.app.prod", "com.example.EntryActivity"),
            DeviceTarget("serial", "phone", DeviceKind.ANDROID), null,
        ) { line, _ ->
            calls++
            assertTrue("--help" in line.parametersList.parameters)
            "--variant <string>\n--deviceId <string>\n--appIdSuffix <string>"
        }
        assertEquals(1, calls)
        assertEquals(listOf("run-android", "--variant", "prodDebug", "--appId", "com.example.app.prod",
            "--main-activity", "com.example.EntryActivity", "--deviceId", "serial"), command)
        assertFalse(AndroidLaunchPreparation.hasOption("--deviceId <string>", "--device"))
    }

    @Test fun failedBuildDoesNotFallBackToStaleMetadata() {
        val source = temporary.newFolder("android")
        File(source, if (SystemInfo.isWindows) "gradlew.bat" else "gradlew").writeText("")
        output(File(source, "mobile/build/outputs/apk"), "debug", "debug", "com.example.stale")
        val failure = IllegalStateException("build failed")
        val actual = assertThrows(IllegalStateException::class.java) {
            AndroidLaunchPreparation.prepare(temporary.root.path, AndroidRunSettings(), null, null) { line, _ ->
                when {
                    "--help" in line.parametersList.parameters -> "--mode <string>"
                    "config" in line.parametersList.parameters -> config(source)
                    else -> throw failure
                }
            }
        }
        assertSame(failure, actual)
    }

    @Test fun overridesAreScopedToTheAppAndRejectTaskInjection() {
        assertNotEquals(AndroidRunSettings.key("app-one", "mode"), AndroidRunSettings.key("app-two", "mode"))
        assertNotEquals(AndroidRunSettings.key("app-one", "mode"), AndroidRunSettings.key("app-one", "applicationId"))
        assertThrows(IllegalArgumentException::class.java) { AndroidRunSettings("debug :app:clean").validate() }
        assertThrows(IllegalArgumentException::class.java) { AndroidRunSettings(applicationId = "--bad").validate() }
        AndroidRunSettings(mainActivity = ".MainActivity").validate()
    }
}
