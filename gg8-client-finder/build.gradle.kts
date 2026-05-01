// GridGain 8 client AddressFinder.
// `ignite-core` is compileOnly so consumers bring their own GG8 client jar.
//
// TestClientV8 has a `main` method that needs ignite-core at RUNTIME, but we
// intentionally keep the dep `compileOnly` (Option A) — the published JAR stays
// slim, and the gradle plugin task that launches TestClientV8 is responsible
// for assembling the runtime classpath (it adds ignite-core itself). Tests that
// drive TestClientV8 already have ignite-core via `testImplementation`.

dependencies {
    api(project(":client-finder-common"))
    compileOnly("org.gridgain:ignite-core:8.9.18")

    testImplementation("org.gridgain:ignite-core:8.9.18")
}
