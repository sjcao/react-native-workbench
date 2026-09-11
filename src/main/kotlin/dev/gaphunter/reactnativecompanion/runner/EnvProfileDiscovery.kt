// Modified by senjucao: exclude unrelated dot-env-prefixed files.
package dev.gaphunter.reactnativecompanion.runner

import java.io.File

/**
 * Finds `.env*` files at the project root -- the real convention
 * `react-native-config` uses for multi-environment profiles
 * (`.env`, `.env.development`, `.env.staging`, `.env.production`, ...),
 * not an invented format. Returns bare file names (not full paths):
 * `ENVFILE` is set relative to the working directory the react-native
 * CLI process already runs in, matching how react-native-config itself
 * expects it (`ENVFILE=.env.staging npx react-native run-android`).
 *
 * Pure and file-IO-only, no PSI/platform dependency -- testable against
 * a real temp directory without booting the IDE.
 */
object EnvProfileDiscovery {
    fun discover(workDirectory: String): List<String> {
        val files = File(workDirectory).listFiles { file ->
            file.isFile && (file.name == ".env" || file.name.startsWith(".env."))
        }
            ?: return emptyList()
        return files.map { it.name }.sorted()
    }
}
