<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# React Native Workbench Changelog

## [1.0.0] - 2026-09-10

Maintained by senjucao, derived from React Native Companion.

- Compact icon toolbar with localized tooltips, keyboard focus and responsive overflow menu.
- Follow IDE language by default: Chinese uses Simplified Chinese, all other languages use English. Optional overrides in Advanced settings are remembered per project.
- Automatic root/nested app discovery and per-app environment preferences in advanced settings.
- Icon-only session controls and concise device diagnostics with expandable details.

- Theme-aware session workspace with logs occupying the available area.
- Independent command consoles, stop/clear/close actions and exit status.
- Metro cache reset, environment diagnostics and monorepo app-directory input.
- Background process startup, visible launch failures and duplicate-session prevention.
- Platform-aware iOS actions and Android SDK environment-variable discovery.
- New plugin ID, author and publishing credentials; removed upstream rating prompts.
- Updated privacy policy, attribution and publishing instructions.

The following entries document the upstream project history.

## [Unreleased]

### Added

- Discover available physical iPhones and iPads with `xcrun xcdevice
  list`, show them separately from simulators, and pass the selected
  target to `react-native run-ios --udid`.

### Changed

- Upgrade the Kotlin Gradle plugin to 2.3.21 so local Android Studio
  2026.1 platform metadata can be compiled.

## [0.2.3]

### Added

- Review/star CTA: after 5 commands launched from the tool window (Run
  Android/iOS, Start Metro, Build releases), a one-time notification
  asks whether to rate the plugin on Marketplace, with a permanent
  "Don't ask again" option.

## [0.2.2]

### Fixed

- Tool window no longer shows the generic platform icon in the sidebar —
  the real Gap Hunter Labs mark is now declared via `icon=` on
  `<toolWindow>`.

## [0.2.1]

### Fixed

- Tool window content (run/release toolbars, console) was rendering
  flush against the tool window's own border, with no margin — fixed
  with an 8px empty border on the root panel.

## [0.2.0]

### Added

- One-click release bundling: "Build Android Release" runs
  `react-native build-android --mode=release`, "Build iOS Release" runs
  `react-native build-ios --mode=Release` — the real react-native CLI
  bundling commands, same `OSProcessHandler` execution path as
  run-android/run-ios/start.
- Multi-environment variable profiles: an "Environment" dropdown
  auto-discovers `.env*` files at the project root (the real
  `react-native-config` convention) and sets `ENVFILE` for every
  command run from the tool window when a profile other than "(none)"
  is selected.

## [0.1.2]

### Changed

- Added a strict local `verifyPlugin` gate (catches
  `@ApiStatus.OverrideOnly`/`Internal`/`Experimental` API usage and
  compatibility problems before Marketplace's own verifier would) — no
  user-visible change, confirmed passing clean against all 6 target IDEs.

## [0.1.1]

### Added

- Gap Hunter Labs brand icon (`pluginIcon.svg` / `pluginIcon_dark.svg`).

## [0.1.0]

### Added

- Run `react-native run-android` / `run-ios` / `start` from a tool
  window via `OSProcessHandler` (IntelliJ's own async process API) so
  the IDE never freezes while a command runs — the leading paid
  incumbent has recent, repeated reports of exactly that.
- Android/iOS device and simulator picker, parsed directly from real
  `adb devices` / `xcrun simctl list devices` output.

[Unreleased]: https://github.com/GapHunterLabs/react-native-companion/compare/0.2.3...HEAD
[0.2.3]: https://github.com/GapHunterLabs/react-native-companion/compare/0.2.2...0.2.3
[0.2.2]: https://github.com/GapHunterLabs/react-native-companion/compare/0.2.1...0.2.2
[0.2.1]: https://github.com/GapHunterLabs/react-native-companion/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/GapHunterLabs/react-native-companion/compare/0.1.2...0.2.0
[0.1.2]: https://github.com/GapHunterLabs/react-native-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/react-native-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/react-native-companion/commits/0.1.0
