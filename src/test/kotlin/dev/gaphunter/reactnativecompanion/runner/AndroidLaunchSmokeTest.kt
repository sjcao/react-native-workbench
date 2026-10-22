package dev.gaphunter.reactnativecompanion.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with WORKBENCH_ANDROID_SMOKE_PROJECT and WORKBENCH_ANDROID_SMOKE_DEVICE. Installs and launches the app. */
class AndroidLaunchSmokeTest {
    @Test fun launchesTheBuiltApplicationOnAConnectedDevice() {
        val directory = System.getenv("WORKBENCH_ANDROID_SMOKE_PROJECT").orEmpty()
        val serial = System.getenv("WORKBENCH_ANDROID_SMOKE_DEVICE").orEmpty()
        val automatic = System.getenv("WORKBENCH_ANDROID_SMOKE_AUTOMATIC") == "1"
        assumeTrue(directory.isNotBlank() && serial.isNotBlank())
        fun execute(line: GeneralCommandLine): String {
            println(line.commandLineString)
            val handler = ReactNativeCommandRunner.createProcess(line)
            val output = CapturingProcessAdapter()
            handler.addProcessListener(output)
            handler.startNotify()
            try {
                assertTrue("Process timed out", handler.waitFor(20 * 60 * 1000L))
                println((output.output.stdout + output.output.stderr).takeLast(6000))
                assertEquals("Process failed: ${output.output.stderr}", 0, handler.exitCode)
                return output.output.stdout
            } finally {
                if (!handler.isProcessTerminated) handler.destroyProcess()
            }
        }
        if (automatic) {
            val connected = DeviceOutputParser.parseAdbDevices(execute(ReactNativeCommandRunner.runAdbDevices())).filter { it.isUsable }
            assertEquals("Automatic smoke testing requires exactly the authorized device", listOf(serial), connected.map { it.serial })
        }
        val command = AndroidLaunchPreparation.prepare(
            directory, AndroidRunSettings(), if (automatic) null else DeviceTarget(serial, serial, DeviceKind.ANDROID), null,
        ) { line, _ -> execute(line) }
        val applicationId = command[command.indexOf("--appId") + 1]
        val launched = execute(ReactNativeCommandRunner.commandLine(directory, command + "--no-packager"))
        assertFalse(launched.contains("Error type 3"))
        val activity = execute(GeneralCommandLine(ReactNativeCommandRunner.adbExecutable(), "-s", serial,
            "shell", "dumpsys", "activity", "activities"))
        assertTrue("Built package must be foreground: $applicationId", activity.lineSequence().any {
            ("mResumedActivity" in it || "topResumedActivity" in it) && "$applicationId/" in it
        })
        println("Verified foreground Android package: $applicationId")
    }
}
