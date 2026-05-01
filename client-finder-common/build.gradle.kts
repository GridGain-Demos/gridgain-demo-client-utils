// Shared base for all client AddressFinder libraries.
// Parses `client-endpoints.yaml` emitted by the gridgain-demo-gradle-plugin.
// Pure Java, no GridGain dependencies; SnakeYAML pinned to 1.33 (see root build.gradle.kts).

dependencies {
    implementation("org.yaml:snakeyaml:1.33")
}
