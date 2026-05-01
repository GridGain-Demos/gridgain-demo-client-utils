import java.util.concurrent.TimeUnit

// Root project for gridgain-demo-client-utils.
//
// This project hosts pure-Java client AddressFinder libraries that consume the
// `client-endpoints.yaml` artifact emitted by the gridgain-demo-gradle-plugin.
// The version is pinned in lock-step with the plugin and the UI project (see CLAUDE.md).
//
// No source lives at the root; only subprojects.

allprojects {
    group = "com.gridgain.demo"
    version = "0.0.5-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
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
        mavenLocal()
    }

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }

    // SnakeYAML is forced to 1.33 across all configurations to prevent
    // Android variant conflicts. This mirrors the plugin / template projects.
    configurations.all {
        resolutionStrategy {
            force("org.yaml:snakeyaml:1.33")
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }

    dependencies {
        // Common test dependencies for every subproject.
        "testImplementation"(platform("org.junit:junit-bom:5.10.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.0")
        "testImplementation"("org.assertj:assertj-core:3.24.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
        repositories {
            maven {
                name = "GridGainNexus"
                if (!project.version.toString().endsWith("-SNAPSHOT")) {
                    throw GradleException(
                        "Publishing to GridGain Nexus currently supports SNAPSHOT versions only. " +
                            "Current version is '${project.version}'. Either set a -SNAPSHOT version, or " +
                            "request a release repository from IT and update build.gradle.kts to target it."
                    )
                }
                url = uri("https://nexus.gridgain.com/repository/public-snapshots/")
                credentials {
                    username = project.findProperty("gridgainNexusUsername") as String? ?: ""
                    password = project.findProperty("gridgainNexusPassword") as String? ?: ""
                }
            }
        }
    }
}
