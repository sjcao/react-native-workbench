package dev.gaphunter.reactnativecompanion.runner

enum class DeviceKind {
    ANDROID,
    IOS_SIMULATOR,
    IOS_PHYSICAL,
}

data class DeviceTarget(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val detail: String = "",
) {
    override fun toString(): String = when (kind) {
        DeviceKind.ANDROID -> "Android: $name"
        DeviceKind.IOS_SIMULATOR -> "iOS Simulator: $name"
        DeviceKind.IOS_PHYSICAL -> "iOS Device: $name${detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    }
}

object DeviceTargetArguments {
    fun forCommand(args: List<String>, target: DeviceTarget?): List<String> {
        if (target == null || args.isEmpty()) return args

        return when {
            args.first() == "run-ios" && target.kind in setOf(DeviceKind.IOS_SIMULATOR, DeviceKind.IOS_PHYSICAL) ->
                args + listOf("--udid", target.id)
            args.first() == "run-android" && target.kind == DeviceKind.ANDROID ->
                args + listOf("--device", target.id)
            else -> args
        }
    }
}
