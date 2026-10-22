# React Native Workbench

Version **1.0.4**, maintained by **senjucao**.

A JetBrains IDE workbench for React Native CLI development: a compact icon toolbar, automatic app discovery, separate session consoles, stop controls, Metro cache reset, release builds and environment diagnostics. Adapts to IDE light and dark themes.

## Quick start

1. Install the plugin ZIP using Settings → Plugins → gear → Install Plugin from Disk.
2. Open **RN Workbench** in the bottom tool-window area.
3. The plugin identifies the root app, searches up to three parent levels and up to three child levels for React Native apps. A single app is selected automatically; choose an app if several are found.
4. Devices are discovered automatically. Keep automatic selection or choose a specific target.
5. Hover over the toolbar icons for descriptions, start Metro and run Android or iOS. Each command has its own console and exit status.

The interface follows the IDE language by default: Chinese IDEs use Simplified Chinese; all other languages use English. Plugin messages are translated; output from external development tools is shown unchanged.

The always-visible More menu contains cache reset, release builds, diagnostics, refresh and advanced settings. On narrow windows, run actions move into this menu. Directory and environment overrides are optional, stored locally per project/app, and default ENVFILE is never overridden automatically. Use Advanced settings to select an app directory outside the search range. Re-identify project clears the remembered directory and scans again.

Stop an existing session before starting the same command again. Clear output preserves the running process. Close session is available after the process exits.

## Android variants and application IDs

Run Android first queries the project-local CLI configuration and assembles the selected variant. It reads the exact `applicationId` from that variant's APK `output-metadata.json`, then runs the CLI with `--appId` to install and launch it. Debug suffixes, flavor suffixes and IDs computed by Gradle are supported without editing the app's `react-native.config.js`. Installation, Metro setup and device selection remain handled by the project's CLI. The install stage may run Gradle again, normally reusing the completed build.

Use More → Advanced settings to set the full Android variant (for example `demoDebug`), an optional full application ID, and an optional main Activity. Empty fields use `debug`, build output discovery, and the CLI's Activity configuration respectively. Overrides are stored separately for each app and affect Run Android; release build actions retain their existing behavior. Older CLI versions using `--variant` / `--deviceId` are detected through local command help.

If the Android module uses a custom build directory, or its Gradle plugin does not produce APK output metadata, set the full application ID explicitly. This skips the preliminary assembly and leaves the build to `run-android`. Failed builds and missing or ambiguous metadata stop the launch with an actionable error.

When a device is explicitly selected, installs use `adb install --no-streaming`. This avoids streamed-install failures observed with the local SDK/device combination. A small plugin-owned Node bridge invokes the project's local React Native CLI and adds only this adb flag; it preserves the target serial, install options, Metro, Activity launch and process cancellation. It does not modify the project or SDK. Automatic device mode keeps the CLI's Gradle installation path.

## Requirements and limits

- Node.js / npx on the IDE's PATH and installed project-local React Native CLI dependencies. Commands use npx --no-install to avoid implicit package installation.
- Android SDK with adb on PATH or ANDROID_HOME / ANDROID_SDK_ROOT configured.
- iOS requires macOS and Xcode. iOS actions are disabled on other systems.
- Environment files use ENVFILE, requiring app-side support such as react-native-config.
- Release commands depend on the project's CLI version and native signing configuration.
- This plugin assists CLI/log debugging; JavaScript breakpoint debugging remains in React Native DevTools.

## Development

Use JDK 21:

```sh
./gradlew test buildPlugin
./gradlew runIde
./gradlew verifyPlugin
```

An installed IDE can be used with `-PlocalIdePath="D:/Program Files/Android/Android Studio"`. The local platform may be newer than the minimum supported IDE; verify compatibility before publishing.

`buildPlugin` also runs `verifyPluginArchive`, which checks the ZIP and nested JAR contents and writes `build/reports/pluginArchive/integrity.txt` with the artifact's SHA-256. Verification and publication depend on this check. `test` also runs the Node transport regression tests. Pass `-PverificationIdePath=<extracted IDE directory>` to verify against a different local IDE while keeping the compilation platform unchanged.

Output: `build/distributions/react-native-workbench-1.0.4.zip`.

An optional device smoke test installs and launches a real app. Set `WORKBENCH_ANDROID_SMOKE_PROJECT` to the app root and `WORKBENCH_ANDROID_SMOKE_DEVICE` to an adb serial, then run `./gradlew test --tests '*AndroidLaunchSmokeTest'`. It is skipped when those variables are absent.
Set `WORKBENCH_ANDROID_SMOKE_AUTOMATIC=1` to test the default automatic device mode instead; this requires exactly one connected device matching the authorized serial.
See [publishing guide](docs/PUBLISHING.md) for release preparation.

## Attribution

Maintained by senjucao. Based on React Native Companion by Gap Hunter Labs, distributed under Apache-2.0. Original source packages are retained for implementation continuity; plugin identity and vendor are independent. See LICENSE and NOTICE.
