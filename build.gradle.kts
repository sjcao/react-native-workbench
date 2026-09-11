import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

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
            val localIdePath = providers.gradleProperty("localIdePath")
            if (localIdePath.isPresent) local(file(localIdePath.get())) else recommended()
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
