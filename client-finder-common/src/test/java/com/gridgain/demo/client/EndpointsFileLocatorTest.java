package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndpointsFileLocatorTest {

    @Test
    void resolvesViaSystemProperty(@TempDir Path tmp) throws IOException {
        Path file = writeEmpty(tmp);
        Map<String, String> sysProps = Map.of(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        Path located = EndpointsFileLocator.locate(
            lookupOf(sysProps),
            lookupOf(Map.of()),
            emptyClassLoader()
        );
        assertThat(located).isEqualTo(file);
    }

    @Test
    void resolvesViaEnvironmentVariable(@TempDir Path tmp) throws IOException {
        Path file = writeEmpty(tmp);
        Map<String, String> env = Map.of(EndpointsFileLocator.ENV_VAR, file.toString());
        Path located = EndpointsFileLocator.locate(
            lookupOf(Map.of()),
            lookupOf(env),
            emptyClassLoader()
        );
        assertThat(located).isEqualTo(file);
    }

    @Test
    void systemPropertyWinsOverEnvironmentVariable(@TempDir Path tmp) throws IOException {
        Path propFile = tmp.resolve("from-prop.yaml");
        Path envFile = tmp.resolve("from-env.yaml");
        Files.writeString(propFile, "x", StandardCharsets.UTF_8);
        Files.writeString(envFile, "x", StandardCharsets.UTF_8);

        Map<String, String> sysProps = Map.of(EndpointsFileLocator.SYSTEM_PROPERTY, propFile.toString());
        Map<String, String> env = Map.of(EndpointsFileLocator.ENV_VAR, envFile.toString());

        Path located = EndpointsFileLocator.locate(
            lookupOf(sysProps),
            lookupOf(env),
            emptyClassLoader()
        );
        assertThat(located).isEqualTo(propFile);
    }

    @Test
    void resolvesViaClasspathResource() throws IOException {
        // The fixture lives at src/test/resources/gridgain-demo/client-endpoints.yaml,
        // which is on the test classpath via the default classloader.
        Path located = EndpointsFileLocator.locate(
            lookupOf(Map.of()),
            lookupOf(Map.of()),
            EndpointsFileLocatorTest.class.getClassLoader()
        );
        assertThat(located).exists();
        // It was extracted to a temp file, so its contents must mirror the fixture.
        String content = Files.readString(located, StandardCharsets.UTF_8);
        assertThat(content).contains("schema_version: 1");
        assertThat(content).contains("trip-cluster");
    }

    @Test
    void fallsThroughToCwdDefaultWhenAvailable(@TempDir Path tmpHome) throws IOException {
        // Create a fake build/demo-output/client/client-endpoints.yaml under tmpHome,
        // then point user.dir at tmpHome. Restore user.dir afterwards so we don't
        // interfere with other tests.
        Path target = tmpHome.resolve("build/demo-output/client/client-endpoints.yaml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "schema_version: 1", StandardCharsets.UTF_8);

        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmpHome.toString());
        try {
            Path located = EndpointsFileLocator.locate(
                lookupOf(Map.of()),
                lookupOf(Map.of()),
                emptyClassLoader()
            );
            assertThat(located).isEqualTo(target);
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void throwsRichErrorWhenAllResolutionStepsFail(@TempDir Path tmpHome) {
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmpHome.toString());
        try {
            assertThatThrownBy(() -> EndpointsFileLocator.locate(
                lookupOf(Map.of()),
                lookupOf(Map.of()),
                emptyClassLoader()
            ))
                .isInstanceOf(MissingEndpointsFileException.class)
                .hasMessageContaining("System property -Dgg.demo.client.endpoints")
                .hasMessageContaining("Environment variable GG_DEMO_CLIENT_ENDPOINTS")
                .hasMessageContaining("Classpath resource '/gridgain-demo/client-endpoints.yaml'")
                .hasMessageContaining("build/demo-output/client/client-endpoints.yaml");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void mentionsOverrideValueInErrorWhenSetButUnreadable(@TempDir Path tmpHome) {
        String oldUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tmpHome.toString());
        Map<String, String> sysProps = Map.of(
            EndpointsFileLocator.SYSTEM_PROPERTY,
            "/this/path/does/not/exist.yaml"
        );
        try {
            assertThatThrownBy(() -> EndpointsFileLocator.locate(
                lookupOf(sysProps),
                lookupOf(Map.of()),
                emptyClassLoader()
            ))
                .isInstanceOf(MissingEndpointsFileException.class)
                .hasMessageContaining("/this/path/does/not/exist.yaml");
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    void blankSystemPropertyDoesNotShortCircuit(@TempDir Path tmp) throws IOException {
        // If the system property is set but blank, we treat it as "not set" and
        // continue to the next resolution step — here, the env var.
        Path file = writeEmpty(tmp);
        Map<String, String> sysProps = Map.of(EndpointsFileLocator.SYSTEM_PROPERTY, "   ");
        Map<String, String> env = Map.of(EndpointsFileLocator.ENV_VAR, file.toString());

        Path located = EndpointsFileLocator.locate(
            lookupOf(sysProps),
            lookupOf(env),
            emptyClassLoader()
        );
        assertThat(located).isEqualTo(file);
    }

    private static Path writeEmpty(Path dir) throws IOException {
        Path file = dir.resolve("client-endpoints.yaml");
        Files.writeString(file, "x", StandardCharsets.UTF_8);
        return file;
    }

    /** A classloader with no parent and no URLs — no resources discoverable. */
    private static ClassLoader emptyClassLoader() {
        return new URLClassLoader(new URL[0], null);
    }

    private static Function<String, String> lookupOf(Map<String, String> backing) {
        return backing::get;
    }
}
