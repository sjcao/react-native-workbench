package dev.gaphunter.reactnativecompanion.runner

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import org.junit.Assume.assumeNoException

class AppDiscoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun app(path: String, dev: Boolean = false): File {
        val folder = File(temporary.root, path).apply { mkdirs() }
        File(folder, "package.json").writeText("""{"name":"mobile","${if (dev) "devDependencies" else "dependencies"}":{"react-native":"0.75.4"}}""")
        return folder
    }

    @Test fun rootTakesPriority() {
        val root = app(".")
        app("apps/mobile")
        assertEquals(listOf(root.canonicalPath), AppDiscovery.discover(root).map { it.directory })
    }
    @Test fun nestedAppAndDevDependency() {
        val nested = app("apps/mobile", true)
        assertEquals(nested.canonicalPath, AppDiscovery.discover(temporary.root).single().directory)
    }
    @Test fun multipleAppsRemainDistinct() {
        app("apps/one"); app("apps/two")
        assertEquals(2, AppDiscovery.discover(temporary.root).size)
    }
    @Test fun excludesGeneratedHiddenAndDeepDirectories() {
        app("node_modules/a"); app("build/a"); app(".git/a"); app("Pods/a")
        app("one/two/three/four")
        assertTrue(AppDiscovery.discover(temporary.root).isEmpty())
        app("one/two/three")
        assertEquals(1, AppDiscovery.discover(temporary.root).size)
    }
    @Test fun invalidManifestDoesNotHideOtherApps() {
        File(temporary.root, "package.json").writeText("not json")
        app("apps/mobile")
        assertEquals(1, AppDiscovery.discover(temporary.root).size)
    }
    @Test fun staleSavedDirectoryFallsBackToDiscovery() {
        val nested = app("mobile")
        assertEquals(nested.canonicalPath, AppDiscovery.resolve(temporary.root, File(temporary.root, "missing").path).single().directory)
    }
    @Test fun savedSelectionKeepsSiblingAppsAvailable() {
        app("one")
        val chosen = app("two")
        assertEquals(2, AppDiscovery.resolve(temporary.root, chosen.path).size)
    }
    @Test fun manualDirectoryOutsideScanRangeWins() {
        app("one")
        val chosen = app("a/b/c/d")
        assertEquals(chosen.canonicalPath, AppDiscovery.resolve(temporary.root, chosen.path).single().directory)
    }
    @Test fun environmentOverridesArePerAppAndNeverGuessed() {
        assertNotEquals(AppEnvironment.key(app("one").path), AppEnvironment.key(app("two").path))
        assertNull(AppEnvironment.restore(null, listOf(".env.production")))
        assertNull(AppEnvironment.restore(".env.old", listOf(".env.production")))
        assertEquals(".env.local", AppEnvironment.restore(".env.local", listOf(".env.local")))
    }
    @Test fun symbolicDirectoriesAreSkipped() {
        val outside = temporary.newFolder("outside")
        File(outside, "package.json").writeText("""{"dependencies":{"react-native":"*"}}""")
        val root = temporary.newFolder("scan")
        try { Files.createSymbolicLink(File(root, "linked").toPath(), outside.toPath()) }
        catch (e: Exception) { assumeNoException("Symbolic links unavailable on this host", e); return }
        assertTrue(AppDiscovery.discover(root).isEmpty())
    }
}
