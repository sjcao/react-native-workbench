# Privacy — React Native Workbench

Maintainer: senjucao. Effective date: 2026-09-10.

The plugin does not include analytics, advertising or telemetry. It reads package.json manifests for app discovery, device listings and environment-file names locally and shows command output in IDE consoles. Selected app directories and explicit environment filenames are saved in local IDE project preferences. It passes the selected environment filename to child processes via ENVFILE; it does not read environment-file contents itself.

The plugin launches installed development tools, which may access the network, download build dependencies, communicate with devices or use credentials according to your project's configuration. Metro can expose a local development server. These tools operate under their own configuration and policies.

Logs may contain paths, device identifiers or information printed by your project. Review console output before sharing it. No rating prompts or vendor-page links are included.

For support, use the contact information on the senjucao Marketplace listing once published.
