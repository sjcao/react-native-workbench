// Copyright 2026 senjucao. Licensed under Apache-2.0.
package dev.gaphunter.reactnativecompanion.runner

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files

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

    fun discover(root: File): List<NativeApp> {
        inspect(root)?.let { return listOf(it) }
        val found = mutableListOf<NativeApp>()
        fun visit(parent: File, depth: Int) {
            if (depth > 3) return
            parent.listFiles()?.sortedBy { it.name }?.forEach { child ->
                if (child.isDirectory && !child.name.startsWith(".") && child.name !in excluded && !Files.isSymbolicLink(child.toPath())) {
                    val app = inspect(child)
                    if (app != null) found.add(app) else visit(child, depth + 1)
                }
            }
        }
        visit(root, 1)
        return found
    }

    fun resolve(root: File, savedDirectory: String?): List<NativeApp> {
        val saved = savedDirectory?.let { inspect(File(it)) }
        val found = discover(root)
        // Keep sibling apps selectable after restoring a previously selected app.
        return if (saved != null && found.none { it.directory == saved.directory }) listOf(saved) else found
    }
}

/** Settings keys use the full normalized directory so different apps never share overrides. */
object AppEnvironment {
    fun key(directory: String) = "dev.senjucao.workbench.env.${File(directory).canonicalPath}"
    fun restore(saved: String?, profiles: List<String>): String? = saved?.takeIf { it in profiles }
}
