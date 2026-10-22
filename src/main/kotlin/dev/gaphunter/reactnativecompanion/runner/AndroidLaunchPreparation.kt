package dev.gaphunter.reactnativecompanion.runner

import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import java.io.File

/** Per-app overrides. An empty application ID enables discovery from the completed build. */
data class AndroidRunSettings(
    val mode: String = "",
    val applicationId: String = "",
    val mainActivity: String = "",
) {
    val variant: String get() = mode.ifBlank { "debug" }

    fun validate() {
        require(variant.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) { "Invalid Android variant: $variant" }
        require(applicationId.isBlank() || applicationId.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
            "Invalid Android application ID: $applicationId"
        }
        require(mainActivity.isBlank() || mainActivity.matches(Regex("\\.?[A-Za-z_$][A-Za-z0-9_.$]*"))) {
            "Invalid Android activity: $mainActivity"
        }
    }

    companion object {
        fun key(directory: String, field: String) = "dev.senjucao.workbench.android.$field.${File(directory).canonicalPath}"
    }
}

object AndroidLaunchPreparation {
    /** Runs on the session's worker thread. Every subprocess is owned by that session for cancellation. */
    fun prepare(
        directory: String,
        settings: AndroidRunSettings,
        target: DeviceTarget?,
        environment: String?,
        execute: (GeneralCommandLine, Boolean) -> String,
    ): List<String> {
        settings.validate()
        fun cli(args: List<String>) = ReactNativeCommandRunner.commandLine(directory, args, environment)
        val help = execute(cli(listOf("run-android", "--help")), false)
        val modeOption = when {
            hasOption(help, "--mode") -> "--mode"
            hasOption(help, "--variant") -> "--variant"
            else -> error("This React Native CLI has no Android variant option. Update the project-local CLI.")
        }
        val applicationId = settings.applicationId.ifBlank {
            val config = execute(cli(listOf("config")), false)
            val android = JsonParser.parseString(config).asJsonObject.getAsJsonObject("project")
                ?.getAsJsonObject("android") ?: error("React Native CLI did not report an Android project.")
            val sourceDir = android.get("sourceDir")?.asString?.let { File(it) }
                ?: error("React Native CLI did not report the Android source directory.")
            val appName = android.get("appName")?.asString?.takeIf { it.isNotBlank() }
                ?: error("React Native CLI did not report the Android application module.")
            require(appName.matches(Regex(":?[A-Za-z0-9_.-]+([:/][A-Za-z0-9_.-]+)*"))) { "Invalid Android module: $appName" }
            val module = appName.trim(':').replace('/', ':')
            val taskVariant = settings.variant.replaceFirstChar { it.uppercaseChar() }
            val wrapper = File(sourceDir, if (SystemInfo.isWindows) "gradlew.bat" else "gradlew")
            require(wrapper.isFile) { "Gradle wrapper not found: $wrapper" }
            val build = GeneralCommandLine(wrapper.absolutePath)
                .withWorkDirectory(sourceDir).withCharset(Charsets.UTF_8)
                .withParameters(":$module:assemble$taskVariant", "-PreactNativeDevServerPort=${System.getenv("RCT_METRO_PORT") ?: "8081"}")
            if (environment != null) build.withEnvironment("ENVFILE", environment)
            execute(build, true) // Never read old metadata after a failed/cancelled build.
            val moduleDirectory = File(sourceDir, module.replace(':', '/'))
            applicationIdFromOutputs(File(moduleDirectory, "build/outputs/apk"), settings.variant)
        }
        return buildList {
            addAll(listOf("run-android", modeOption, settings.variant, "--appId", applicationId))
            if (settings.mainActivity.isNotBlank()) addAll(listOf("--main-activity", settings.mainActivity))
            if (target != null && target.kind == DeviceKind.ANDROID && target.id.isNotBlank()) {
                val option = if (hasOption(help, "--device")) "--device" else "--deviceId"
                require(hasOption(help, option)) { "This React Native CLI cannot select an Android device." }
                addAll(listOf(option, target.id))
            }
        }
    }

    internal fun hasOption(help: String, option: String): Boolean =
        Regex("(?m)(?:^|\\s)${Regex.escape(option)}(?=\\s|[=<,]|$)").containsMatchIn(help)

    internal fun applicationIdFromOutputs(outputs: File, variant: String): String {
        val ids = outputs.walkTopDown().maxDepth(8)
            .filter { it.isFile && it.name == "output-metadata.json" && it.length() <= 2_000_000 }
            .mapNotNull { metadata ->
                runCatching {
                    val json = metadata.reader().use { JsonParser.parseReader(it) }.asJsonObject
                    if (!json.get("variantName")?.asString.equals(variant, ignoreCase = true)) return@runCatching null
                    // Ignore metadata whose APK outputs no longer exist (e.g. after a partial clean).
                    val elements = json.getAsJsonArray("elements") ?: return@runCatching null
                    if (elements.isEmpty || elements.any { !File(metadata.parentFile, it.asJsonObject.get("outputFile").asString).isFile }) {
                        return@runCatching null
                    }
                    json.get("applicationId")?.asString?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }.toSet()
        require(ids.size == 1) {
            "Cannot identify one application ID for Android variant '$variant' in $outputs. " +
                "Set the full Android application ID in RN Workbench advanced settings (custom build directories and older Gradle plugins may require this)."
        }
        return ids.single().also { AndroidRunSettings(applicationId = it).validate() }
    }
}
