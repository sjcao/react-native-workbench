package dev.gaphunter.reactnativecompanion.runner

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EnvProfileDiscoveryTest {
    @Test
    fun `unrelated env prefixed files are excluded`() {
        File(tempDir, ".environment").writeText("unrelated")
        File(tempDir, ".envrc").writeText("shell config")
        File(tempDir, ".env.local").writeText("APP_MODE=local")
        assertEquals(listOf(".env.local"), EnvProfileDiscovery.discover(tempDir.absolutePath))
    }

    private lateinit var tempDir: File

    @Before
    fun createTempDir() {
        tempDir = Files.createTempDirectory("react-native-companion-test").toFile()
    }

    @After
    fun deleteTempDir() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `finds env files at the project root sorted by name`() {
        File(tempDir, ".env.staging").writeText("API_URL=https://staging.acme-corp.com")
        File(tempDir, ".env").writeText("API_URL=https://dev.acme-corp.com")
        File(tempDir, ".env.production").writeText("API_URL=https://api.acme-corp.com")
        File(tempDir, "package.json").writeText("{}")

        val profiles = EnvProfileDiscovery.discover(tempDir.absolutePath)

        assertEquals(listOf(".env", ".env.production", ".env.staging"), profiles)
    }

    @Test
    fun `a project with no env files returns an empty list, not a crash`() {
        File(tempDir, "package.json").writeText("{}")
        assertTrue(EnvProfileDiscovery.discover(tempDir.absolutePath).isEmpty())
    }

    @Test
    fun `a nonexistent directory returns an empty list, not a crash`() {
        assertTrue(EnvProfileDiscovery.discover(File(tempDir, "does-not-exist").absolutePath).isEmpty())
    }

    @Test
    fun `subdirectories starting with dot-env are not treated as files`() {
        File(tempDir, ".env.local").mkdir()
        assertTrue(EnvProfileDiscovery.discover(tempDir.absolutePath).isEmpty())
    }
}
