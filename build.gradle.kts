import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

@CacheableTask
abstract class VerifyPluginArchive : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty
    @get:Input abstract val expectedId: Property<String>
    @get:Input abstract val expectedVersion: Property<String>
    @get:OutputFile abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val archive = archiveFile.get().asFile
        val names = mutableSetOf<String>()
        val roots = mutableSetOf<String>()
        var descriptors = 0
        var jarCount = 0
        var totalSize = 0L
        fun checkPath(name: String) {
            require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name &&
                name.split('/').none { it == ".." || it == "." }) { "Unsafe archive path: $name" }
        }
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                checkPath(entry.name)
                require(names.add(entry.name)) { "Duplicate ZIP entry: ${entry.name}" }
                roots.add(entry.name.substringBefore('/'))
                require(entry.method == 0 || entry.method == 8) { "Unsupported ZIP compression: ${entry.name}" }
                require(entry.size in 0..128L * 1024 * 1024) { "Invalid or oversized ZIP entry: ${entry.name}" }
                val payload = zip.getInputStream(entry).use { it.readBytes() }
                require(payload.size.toLong() == entry.size && CRC32().apply { update(payload) }.value == entry.crc) {
                    "ZIP size/CRC mismatch: ${entry.name}"
                }
                totalSize += payload.size
                require(totalSize <= 512L * 1024 * 1024) { "Expanded plugin is unexpectedly large" }
                if (entry.isDirectory) continue
                require(entry.name.matches(Regex("[^/]+/lib/[^/]+\\.jar"))) { "Unexpected plugin ZIP layout: ${entry.name}" }
                jarCount++
                val jarNames = mutableSetOf<String>()
                ZipInputStream(ByteArrayInputStream(payload)).use { jar ->
                    while (true) {
                        val item = jar.nextEntry ?: break
                        checkPath(item.name)
                        require(jarNames.add(item.name)) { "Duplicate JAR entry: ${item.name}" }
                        // Consuming every entry validates the nested JAR's decompression and CRC.
                        val bytes = jar.readBytes()
                        jar.closeEntry()
                        if (item.name == "META-INF/plugin.xml") {
                            descriptors++
                            val factory = DocumentBuilderFactory.newInstance().apply {
                                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                            }
                            val descriptor = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
                            require(descriptor.documentElement.tagName == "idea-plugin") { "Invalid plugin descriptor" }
                            require(descriptor.getElementsByTagName("id").item(0)?.textContent == expectedId.get()) { "Wrong plugin ID in ZIP" }
                            require(descriptor.getElementsByTagName("version").item(0)?.textContent == expectedVersion.get()) { "Wrong plugin version in ZIP" }
                        }
                    }
                }
                require(jarNames.isNotEmpty()) { "Empty or invalid JAR: ${entry.name}" }
            }
        }
        require(roots.size == 1 && jarCount > 0 && descriptors == 1) { "Plugin must have one root, lib JARs and exactly one plugin.xml" }
        val hash = MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText("Archive: ${archive.name}\nSHA-256: $hash\nJARs: $jarCount\nZIP/JAR CRC, paths, structure, ID and version: PASS\n")
        logger.lifecycle("Plugin archive integrity: PASS ($jarCount JARs, SHA-256 $hash)")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.withType<Zip>().configureEach {
    setMetadataCharset("UTF-8")
    isZip64 = false
}

val pluginArchive = tasks.named<Zip>("buildPlugin")
val verifyPluginArchive = tasks.register<VerifyPluginArchive>("verifyPluginArchive") {
    group = "verification"
    description = "Validate the distributable ZIP and every nested JAR before verification or publication."
    archiveFile.set(pluginArchive.flatMap { it.archiveFile })
    expectedId.set("dev.senjucao.reactnativeworkbench")
    expectedVersion.set(project.version.toString())
    reportFile.set(layout.buildDirectory.file("reports/pluginArchive/integrity.txt"))
}
pluginArchive.configure { finalizedBy(verifyPluginArchive) }
tasks.named("verifyPlugin") { dependsOn(verifyPluginArchive) }
tasks.named("publishPlugin") { dependsOn(verifyPluginArchive) }
tasks.named("check") { dependsOn(verifyPluginArchive) }

val testAndroidCli = tasks.register<Exec>("testAndroidCli") {
    group = "verification"
    description = "Test Android adb transport compatibility without connecting to a device."
    inputs.files("src/main/resources/runner/android-cli.cjs", "src/test/js/android-cli.test.cjs")
    commandLine("node", "--test", "src/test/js/android-cli.test.cjs")
}
tasks.named("test") { dependsOn(testAndroidCli) }

tasks.jar {
    from(files("LICENSE", "NOTICE")) { into("META-INF") }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // For local development, pass -PlocalIdePath=<IDE installation>
        // to avoid downloading a platform distribution. CI keeps using the
        // pinned IDEA version below for reproducible builds.
        val localIdePath = providers.gradleProperty("localIdePath")
        if (localIdePath.isPresent) {
            local(localIdePath.get())
        } else {
            intellijIdea("2025.2.6.2")
        }

        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 243 = 2024.3, so as not to exclude the real installed base.
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }

    // Same tooling bug as the Ansible Companion plugin (Gradle 9.5 +
    // IntelliJ Platform Gradle Plugin 2.16 + IDE 2025.2.6.2): the
    // bytecode instrumenter fails with "instrumentIdeaExtensions
    // doesn't support the nested element". Not required for
    // build/test/verifyPlugin.
    instrumentCode = false

    // Catch experimental/internal API usage locally, before Marketplace's
    // own verifier flags it post-upload.
    pluginVerification {
        ides {
            val verificationIdePath = providers.gradleProperty("verificationIdePath")
            val localIdePath = providers.gradleProperty("localIdePath")
            when {
                verificationIdePath.isPresent -> local(file(verificationIdePath.get()))
                localIdePath.isPresent -> local(file(localIdePath.get()))
                else -> recommended()
            }
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )
    }

    // Release credentials are supplied through the publisher's environment.
    publishing {
        token.set(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN"))
    }

    // Optional signing material is never committed to the repository.
    signing {
        certificateChain.set(providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY"))
        password.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD"))
    }
}
