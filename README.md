# React Native Workbench

Version **1.0.2**, maintained by **senjucao**.

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

Output: `build/distributions/react-native-workbench-1.0.2.zip`.
See [publishing guide](docs/PUBLISHING.md) for release preparation.

## Attribution

Maintained by senjucao. Based on React Native Companion by Gap Hunter Labs, distributed under Apache-2.0. Original source packages are retained for implementation continuity; plugin identity and vendor are independent. See LICENSE and NOTICE.
