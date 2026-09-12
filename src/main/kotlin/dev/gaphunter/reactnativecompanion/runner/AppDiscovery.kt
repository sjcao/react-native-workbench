// Copyright 2026 senjucao. Licensed under Apache-2.0.
package dev.gaphunter.reactnativecompanion.runner

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class NativeApp(val directory: String, val name: String) {
    override fun toString() = name
}

object AppDiscovery {
    private val excluded = setOf("node_modules", "build", "dist", "out", "coverage", "Pods", "vendor")

    fun inspect(directory: File): NativeApp? = try {
        val manifest = File(directory, "package.json")
        if (!manifest.isFile || manifest.length() > 2_000_000) null else {
            val json = manifest.reader().use { JsonParser.parseReader(it) }.asJsonObject
            val native = listOf("dependencies", "devDependencies").any {
                json.get(it)?.takeIf { value -> value.isJsonObject }?.asJsonObject?.has("react-native") == true
            }
            if (!native) null else NativeApp(directory.canonicalPath,
                json.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: directory.name)
        }
    } catch (_: Exception) { null }

    fun discover(root: File): List<NativeApp> = discover(root) { false }

    fun discover(root: File, cancelled: () -> Boolean): List<NativeApp> {
        inspect(root)?.let { return listOf(it) }
        val found = mutableListOf<NativeApp>()
        val rootPath = runCatching { root.canonicalFile.toPath().normalize() }.getOrNull() ?: return found
        val visited = mutableSetOf<Path>()

        fun isInsideRoot(path: Path): Boolean = path == rootPath || path.startsWith(rootPath)

        fun visit(parent: File, depth: Int) {
            if (depth > 3 || cancelled()) return
            val parentPath = runCatching { parent.canonicalFile.toPath().normalize() }.getOrNull() ?: return
            if (!isInsideRoot(parentPath) || !visited.add(parentPath)) return
            parent.listFiles()?.sortedBy { it.name }?.forEach { child ->
                if (cancelled()) return
                val childPath = runCatching { child.canonicalFile.toPath().normalize() }.getOrNull() ?: return@forEach
                if (child.isDirectory && !child.name.startsWith(".") && child.name !in excluded &&
                    !Files.isSymbolicLink(child.toPath()) && isInsideRoot(childPath)) {
                    val app = inspect(child)
                    if (app != null) found.add(app) else visit(child, depth + 1)
                }
            }
        }
        visit(root, 1)
        return found
    }

    /**
     * Searches around the current project: the current directory is searched
     * up to three levels down, while the current directory and up to three
     * parents are checked as possible app roots. Results are de-duplicated by
     * their canonical directory without recursively scanning large parent
     * directories.
     */
    fun discoverAround(root: File): List<NativeApp> = discoverAround(root) { false }

    fun discoverAround(root: File, cancelled: () -> Boolean): List<NativeApp> {
        val current = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val found = linkedMapOf<String, NativeApp>()
        discover(current, cancelled).forEach { app -> found.putIfAbsent(app.directory, app) }
        var parent = current.parentFile
        repeat(3) {
            if (cancelled()) return@repeat
            parent?.let { inspect(it)?.let { app -> found.putIfAbsent(app.directory, app) } }
            parent = parent?.parentFile
        }
        return found.values.toList()
    }

    fun resolve(root: File, savedDirectory: String?): List<NativeApp> = resolve(root, savedDirectory) { false }

    fun resolve(root: File, savedDirectory: String?, cancelled: () -> Boolean): List<NativeApp> {
        if (cancelled()) return emptyList()
        val saved = savedDirectory?.let { inspect(File(it)) }
        val found = discoverAround(root, cancelled)
        // Keep sibling apps selectable after restoring a previously selected app.
        return if (saved != null && found.none { it.directory == saved.directory }) listOf(saved) else found
    }
}

/** Settings keys use the full normalized directory so different apps never share overrides. */
object AppEnvironment {
    fun key(directory: String) = "dev.senjucao.workbench.env.${File(directory).canonicalPath}"
    fun restore(saved: String?, profiles: List<String>): String? = saved?.takeIf { it in profiles }
}
