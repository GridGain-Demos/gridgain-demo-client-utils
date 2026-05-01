// OCI image build for the GG8 test client.
//
// This module is image-only: it does not produce a publishable Maven artifact.
// Its job is to bundle `gg8-client-finder` (which contains `TestClientV8`) along
// with the GG8 client runtime into a container image via Jib. Library consumers
// continue to use the slim `gg8-client-finder` JAR (which keeps `ignite-core`
// as `compileOnly`); the image module is a separate, fatter bundle whose
// runtime classpath includes `ignite-core` directly.

plugins {
    java
    id("com.google.cloud.tools.jib") version "3.4.4"
}

description = "OCI image build for the GG8 test client"

dependencies {
    implementation(project(":gg8-client-finder"))
    // implementation, NOT compileOnly: ignite needs to be on the runtime classpath
    // that lands in the image. The published gg8-client-finder JAR keeps ignite
    // as compileOnly so library consumers stay slim; the image module is a
    // separate bundle.
    implementation("org.gridgain:ignite-core:8.9.18")
}

// Default registry: the public GridGain-Demos GHCR org. End users pull from here;
// they never push. Override `imageRegistry` for local Jib builds (e.g.
// `jibBuildTar` for air-gapped distribution) or for forks publishing to a
// private registry.
val DEFAULT_REGISTRY = "ghcr.io/gridgain-demos"
val SOURCE_REPO_URL  = "https://github.com/GridGain-Demos/gridgain-client-utils"

val imageRegistry = ((findProperty("imageRegistry") as String?)?.trimEnd('/'))
    ?.takeIf { it.isNotBlank() }
    ?: DEFAULT_REGISTRY
val imageName    = "demo-test-client-gg8"
val imageTag     = ((findProperty("imageTag") as String?)?.takeIf { it.isNotBlank() })
    ?: project.version.toString()
val targetImage  = "$imageRegistry/$imageName:$imageTag"

jib {
    from {
        image = "eclipse-temurin:17-jre"
    }
    to {
        image = targetImage
        tags  = setOf(imageTag)
        // Auth: reads gridgainGhcrUsername / gridgainGhcrPassword from
        // ~/.gradle/gradle.properties (or -P flags), or falls through to Jib's
        // standard credential helpers (~/.docker/config.json, gcloud, etc.)
        // when those properties aren't set.
        val ghcrUser = (findProperty("gridgainGhcrUsername") as String?)?.takeIf { it.isNotBlank() }
        val ghcrPass = (findProperty("gridgainGhcrPassword") as String?)?.takeIf { it.isNotBlank() }
        if (ghcrUser != null && ghcrPass != null) {
            auth {
                username = ghcrUser
                password = ghcrPass
            }
        }
    }
    container {
        mainClass = "com.gridgain.demo.client.gg8.TestClientV8"
        labels.set(mapOf(
            "org.opencontainers.image.title"       to "GridGain Demo Test Client (GG8)",
            "org.opencontainers.image.version"     to imageTag,
            "org.opencontainers.image.source"      to SOURCE_REPO_URL,
            "org.opencontainers.image.description" to "Built-in test client for GridGain 8 demos. Validates per-node reachability via the demo plugin's client-endpoints ConfigMap.",
            "org.gridgain.demo.major-version"      to "8",
        ))
    }
}

// Image-build modules don't publish to Maven. The root `subprojects { ... }`
// block applies `maven-publish` to every subproject and creates a default
// publication; rather than restructure that, we simply disable the publish
// tasks here so they're no-ops for this module.
tasks.withType<PublishToMavenLocal>().configureEach { enabled = false }
tasks.withType<PublishToMavenRepository>().configureEach { enabled = false }
