// Modified by senjucao: background session creation and process-tree termination.
package dev.gaphunter.reactnativecompanion.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.util.SystemInfo
import java.io.File

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
        var commandLine = GeneralCommandLine(npxExecutable())
            .withParameters(listOf("--no-install", "react-native") + args)
            .withWorkDirectory(File(workDirectory))
            .withCharset(Charsets.UTF_8)
        if (envFile != null) {
            commandLine = commandLine.withEnvironment("ENVFILE", envFile)
        }
        if (SystemInfo.isMac) {
            commandLine = commandLine.withEnvironment("REACT_TERMINAL", iosTerminal())
        }

        return KillableProcessHandler(commandLine).apply { setShouldKillProcessSoftly(false) }
    }

    /**
     * 获取 macOS 下运行 iOS 时打开 Metro 所使用的终端应用。
     * 优先读取环境变量 REACT_TERMINAL（例如用户指定了 iTerm），未设置时默认使用系统终端 Terminal。
     */
    fun iosTerminal(): String =
        System.getenv("REACT_TERMINAL")?.trim()?.takeIf { it.isNotEmpty() } ?: "Terminal"

    fun runAdbDevices(): GeneralCommandLine =
        GeneralCommandLine(adbExecutable(), "devices").withCharset(Charsets.UTF_8)

    private fun adbExecutable(): String {
        val executable = if (SystemInfo.isWindows) "adb.exe" else "adb"
        return sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
            .filterNotNull().map { File(it, "platform-tools/$executable") }
            .firstOrNull { it.isFile }?.absolutePath ?: executable
    }

    fun runSimctlListDevices(): GeneralCommandLine =
        GeneralCommandLine("xcrun", "simctl", "list", "devices").withCharset(Charsets.UTF_8)

    fun runXcdeviceList(): GeneralCommandLine =
        GeneralCommandLine("xcrun", "xcdevice", "list").withCharset(Charsets.UTF_8)

    private fun npxExecutable(): String = if (SystemInfo.isWindows) "npx.cmd" else "npx"
}
