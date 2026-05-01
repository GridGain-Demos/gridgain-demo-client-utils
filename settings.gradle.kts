pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "GridGain External Repository"
            url = uri("https://maven.gridgain.com/nexus/content/repositories/external")
        }
        maven {
            name = "GridGain Beta Repository"
            url = uri("https://maven.gridgain.com/nexus/content/repositories/external-beta")
        }
        maven {
            name = "GridGainNexus"
            url = uri("https://nexus.gridgain.com/repository/public-snapshots/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "gridgain-demo-client-utils"

// Enable stable configuration cache
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

// Enable type-safe project accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":client-finder-common",
    ":gg8-client-finder",
    ":gg9-client-finder",
    ":gg8-test-client-image",
    ":gg9-test-client-image",
)
