// Modified by senjucao: background session creation and process-tree termination.
package dev.gaphunter.reactnativecompanion.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Files

/**
 * The real complaint this plugin exists to fix (verdict_console.md,
 * evidence_9564.md, 2026-04 ★1): "severely impacting IDE performance...
 * PhpStorm frequently becomes unresponsive... reproducible — thread
 * dumps are required." `OSProcessHandler` spawns and reads the process
 * on background threads by construction -- using it (instead of e.g.
 * `Runtime.exec` + a blocking `.waitFor()` on the EDT) is not an
 * optimization here, it IS the fix. Never call anything blocking on
 * this class from the UI thread regardless.
 */
object ReactNativeCommandRunner {
    private val androidCliBridge: File by lazy {
        val bridge = Files.createTempFile("rn-workbench-android-", ".cjs").toFile()
        ReactNativeCommandRunner::class.java.getResourceAsStream("/runner/android-cli.cjs")!!.use { input ->
            bridge.outputStream().use { input.copyTo(it) }
        }
        bridge.deleteOnExit()
        bridge
    }

    /**
     * [envFile], when non-null, is set as the ENVFILE environment
     * variable before spawning -- the real mechanism react-native-config
     * reads to pick a `.env.<profile>` file, not a plugin-invented one.
     */
    fun run(workDirectory: String, args: List<String>, console: ConsoleView, envFile: String? = null): OSProcessHandler {
        val processHandler = create(workDirectory, args, envFile)
        console.attachToProcess(processHandler)
        processHandler.startNotify()
        return processHandler
    }

    fun create(workDirectory: String, args: List<String>, envFile: String? = null): OSProcessHandler {
        return createProcess(commandLine(workDirectory, args, envFile))
    }

    fun createProcess(commandLine: GeneralCommandLine): OSProcessHandler =
        KillableProcessHandler(commandLine).apply { setShouldKillProcessSoftly(false) }

    internal fun commandLine(workDirectory: String, args: List<String>, envFile: String? = null): GeneralCommandLine {
        val useBridge = args.firstOrNull() == "run-android" && "--help" !in args &&
            args.any { it == "--device" || it == "--deviceId" || it.startsWith("--device=") || it.startsWith("--deviceId=") }
        var commandLine = (if (useBridge) GeneralCommandLine("node").withParameters(listOf(androidCliBridge.absolutePath) + args)
            else GeneralCommandLine(npxExecutable()).withParameters(listOf("--no-install", "react-native") + args))
            .withWorkDirectory(File(workDirectory))
            .withCharset(Charsets.UTF_8)
        if (useBridge) commandLine = commandLine.withEnvironment("RN_WORKBENCH_ADB", adbExecutable())
        if (envFile != null) {
            commandLine = commandLine.withEnvironment("ENVFILE", envFile)
        }
        if (SystemInfo.isMac) {
            commandLine = commandLine.withEnvironment("REACT_TERMINAL", iosTerminal())
        }

        return commandLine
    }

    /**
     * 获取 macOS 下运行 iOS 时打开 Metro 所使用的终端应用。
     * 优先读取环境变量 REACT_TERMINAL（例如用户指定了 iTerm），未设置时默认使用系统终端 Terminal。
     */
    fun iosTerminal(): String =
        System.getenv("REACT_TERMINAL")?.trim()?.takeIf { it.isNotEmpty() } ?: "Terminal"

    fun runAdbDevices(): GeneralCommandLine =
        GeneralCommandLine(adbExecutable(), "devices").withCharset(Charsets.UTF_8)

    internal fun adbExecutable(
        environment: Map<String, String> = System.getenv(),
        userDirectory: String = System.getProperty("user.home"),
        windows: Boolean = SystemInfo.isWindows,
        mac: Boolean = SystemInfo.isMac,
    ): String {
        val executable = if (windows) "adb.exe" else "adb"
        val defaultSdk = when {
            mac -> File(userDirectory, "Library/Android/sdk")
            windows -> File(environment["LOCALAPPDATA"] ?: File(userDirectory, "AppData/Local").path, "Android/Sdk")
            else -> File(userDirectory, "Android/Sdk")
        }
        val sdkRoots = ArrayList<File>()
        environment["ANDROID_HOME"]?.takeIf { it.isNotBlank() }?.let(::File)?.let(sdkRoots::add)
        environment["ANDROID_SDK_ROOT"]?.takeIf { it.isNotBlank() }?.let(::File)?.let(sdkRoots::add)
        sdkRoots.add(defaultSdk)
        for (sdkRoot in sdkRoots) {
            val candidate = File(sdkRoot, "platform-tools/$executable")
            if (candidate.isFile) return candidate.absolutePath
        }
        return executable
    }

    fun runSimctlListDevices(): GeneralCommandLine =
        GeneralCommandLine("xcrun", "simctl", "list", "devices", "--json").withCharset(Charsets.UTF_8)

    fun runXcdeviceList(): GeneralCommandLine =
        GeneralCommandLine("xcrun", "xcdevice", "list", "--timeout=5").withCharset(Charsets.UTF_8)

    private fun npxExecutable(): String = if (SystemInfo.isWindows) "npx.cmd" else "npx"
}
