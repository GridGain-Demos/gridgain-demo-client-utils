// GridGain 9 client AddressFinder.
// `ignite-client` is compileOnly so consumers bring their own GG9 client jar.
//
// TestClientV9 has a `main` method that needs ignite-client at RUNTIME, but we
// intentionally keep the dep `compileOnly` (Option A) — the published JAR stays
// slim, and the gradle plugin task that launches TestClientV9 is responsible
// for assembling the runtime classpath (it adds ignite-client itself). Tests
// that drive TestClientV9 already have ignite-client via `testImplementation`.

dependencies {
    api(project(":client-finder-common"))
    compileOnly("org.gridgain:ignite-client:9.1.3")

    testImplementation("org.gridgain:ignite-client:9.1.3")
}
