<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# React Native Workbench Changelog

## [1.0.2] - 2026-09-16

- Fixed device discovery getting stuck when a probe executable cannot start.
- Added macOS default Android SDK discovery for IDEs launched outside a shell.
- Added a bounded timeout to iOS physical-device discovery.

## [1.0.1] - 2026-09-13

- Improved React Native app discovery and nested project handling.
- Improved device output parsing and runtime error handling.
- Improved console session behavior and related test coverage.

## [1.0.0] - 2026-09-10

Maintained by senjucao, derived from React Native Companion.

- Compact icon toolbar with localized tooltips, keyboard focus and responsive overflow menu.
- Follow IDE language by default: Chinese uses Simplified Chinese, all other languages use English.
- Automatic root/nested app discovery and per-app environment preferences in advanced settings.
- Icon-only session controls and concise device diagnostics with expandable details.

- Theme-aware session workspace with logs occupying the available area.
- Independent command consoles, stop/clear/close actions and exit status.
- Metro cache reset, environment diagnostics and monorepo app-directory input.
- Background process startup, visible launch failures and duplicate-session prevention.
- Platform-aware iOS actions and Android SDK environment-variable discovery.
- New plugin ID, author and publishing credentials; removed upstream rating prompts.
- Updated privacy policy, attribution and publishing instructions.
