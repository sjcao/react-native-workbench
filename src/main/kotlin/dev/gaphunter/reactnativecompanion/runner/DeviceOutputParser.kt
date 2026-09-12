// Modified by senjucao: only offer iOS runtime simulators to run-ios.
package dev.gaphunter.reactnativecompanion.runner

import com.google.gson.JsonParser

/**
 * Verdict's own complaint list (out/verdict_console.md): the paid
 * incumbent's iOS simulator/Android emulator integration is "full of
 * bugs", buttons that "mostly don't work". Parsing the real output of
 * `adb devices` and `xcrun simctl list devices` ourselves, rather than
 * whatever the incumbent does, is the actual fix -- kept as pure text
 * parsing (no process execution here) so it's testable without a real
 * adb/Xcode install.
 */
data class AndroidDevice(val serial: String, val state: String) {
    val isUsable: Boolean get() = state == "device"
}

data class IosSimulator(val udid: String, val name: String, val state: String) {
    val isBooted: Boolean get() = state == "Booted"
}

data class IosPhysicalDevice(
    val udid: String,
    val name: String,
    val version: String,
    val connection: String,
)

object DeviceOutputParser {

    /**
     * `adb devices` output looks like:
     * ```
     * List of devices attached
     * emulator-5554	device
     * R58M12ABCDE	unauthorized
     * ```
     */
    fun parseAdbDevices(output: String): List<AndroidDevice> {
        val lines = output.lineSequence().toList()
        val header = lines.indexOfFirst { it.trim() == "List of devices attached" }
        val deviceLines = if (header >= 0) lines.drop(header + 1) else lines
        return deviceLines.asSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 2 || parts[0].isEmpty() || parts[0].startsWith("*")) null
                else AndroidDevice(parts[0], parts[1])
            }
            .toList()
    }

    /**
     * `xcrun simctl list devices --json` is preferred. The human-readable
     * `xcrun simctl list devices` format remains supported as a fallback:
     * ```
     * == Devices ==
     * -- iOS 17.0 --
     *     iPhone 15 (12345678-1234-1234-1234-123456789012) (Shutdown)
     *     iPhone 15 Pro (87654321-4321-4321-4321-210987654321) (Booted)
     * -- tvOS 17.0 --
     *     Apple TV (ABCDEF12-3456-7890-ABCD-EF1234567890) (Shutdown)
     * ```
     * Section headers (`== ... ==`, `-- ... --`) don't match the device
     * line shape and are skipped naturally rather than special-cased.
     */
    private val SIMCTL_DEVICE_LINE = Regex("""^\s*(.+?)\s+\(([0-9A-Fa-f-]{36})\)\s+\((\w+)\)\s*$""")

    fun parseSimctlDevices(output: String): List<IosSimulator> {
        if (output.trimStart().startsWith("{")) return parseSimctlJson(output)

        var iosSection = false
        return output.lineSequence()
            .mapNotNull { line ->
                if (line.trim().startsWith("--")) {
                    iosSection = line.trim().startsWith("-- iOS ")
                    return@mapNotNull null
                }
                if (!iosSection) return@mapNotNull null
                SIMCTL_DEVICE_LINE.matchEntire(line)?.let {
                    IosSimulator(udid = it.groupValues[2], name = it.groupValues[1], state = it.groupValues[3])
                }
            }
            .toList()
    }

    /**
     * JSON output from `xcrun simctl list devices --json` is stable across
     * Xcode versions and avoids relying on the human-readable table format.
     */
    private fun parseSimctlJson(output: String): List<IosSimulator> = try {
        val root = JsonParser.parseString(output)
        val devices = root.asJsonObject.get("devices")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyList()

        devices.entrySet()
            .filter { (runtime, _) -> runtime.contains(".iOS-") }
            .flatMap { (_, values) ->
                if (!values.isJsonArray) return@flatMap emptyList()
                values.asJsonArray.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val device = element.asJsonObject
                    val available = device.get("isAvailable")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                    val udid = device.get("udid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    val name = device.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    if (!available) return@mapNotNull null
                    IosSimulator(
                        udid = udid,
                        name = name,
                        state = device.get("state")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    )
                }
            }
    } catch (_: RuntimeException) {
        emptyList()
    }

    /**
     * `xcrun xcdevice list` returns JSON containing every Apple platform,
     * simulators, and remembered but currently unavailable devices. Only
     * available physical iOS/iPadOS devices are useful run-ios targets.
     */
    fun parseXcdevicePhysicalDevices(output: String): List<IosPhysicalDevice> = try {
        if (output.isBlank()) return emptyList()

        val root = JsonParser.parseString(output)
        if (!root.isJsonArray) return emptyList()

        root.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val device = element.asJsonObject
            val simulator = device.get("simulator")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return@mapNotNull null
            val available = device.get("available")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            val platform = device.get("platform")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val udid = device.get("identifier")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val name = device.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null

            if (simulator || !available || platform != "com.apple.platform.iphoneos") return@mapNotNull null

            IosPhysicalDevice(
                udid = udid,
                name = name,
                version = device.get("operatingSystemVersion")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                connection = device.get("interface")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            )
        }
    } catch (_: RuntimeException) {
        emptyList()
    }
}
