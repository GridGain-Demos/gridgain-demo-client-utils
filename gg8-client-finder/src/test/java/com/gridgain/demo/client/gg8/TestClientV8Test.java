package com.gridgain.demo.client.gg8;

import static org.assertj.core.api.Assertions.assertThat;

import com.gridgain.demo.client.EndpointsFileLocator;
import com.gridgain.demo.client.RuntimeContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TestClientV8} that exercise the {@code run(...)} entry
 * point without actually starting an Ignite client. The connection step is
 * skipped via the {@code --dry-run} flag; we only verify argument validation,
 * address-resolution failures, and version mismatch detection.
 */
class TestClientV8Test {

    private final Map<String, String> savedProps = new HashMap<>();

    private static final String[] PROPS_TO_GUARD = {
        EndpointsFileLocator.SYSTEM_PROPERTY,
        RuntimeContext.SYSTEM_PROPERTY
    };

    @BeforeEach
    void saveProps() {
        for (String key : PROPS_TO_GUARD) {
            savedProps.put(key, System.getProperty(key));
        }
        for (String key : PROPS_TO_GUARD) {
            System.clearProperty(key);
        }
    }

    @AfterEach
    void restoreProps() {
        for (String key : PROPS_TO_GUARD) {
            String saved = savedProps.get(key);
            if (saved == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, saved);
            }
        }
    }

    @Test
    void run_withNoArgs_returnsUsageExitCode() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[0], out, err);

        assertThat(exit).isEqualTo(64);
        assertThat(errBytes.toString(StandardCharsets.UTF_8))
            .contains("Usage:")
            .contains("TestClientV8");
    }

    @Test
    void run_withTooManyArgs_returnsUsageExitCode() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[]{"a", "b", "c"}, out, err);

        assertThat(exit).isEqualTo(64);
        assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("Usage:");
    }

    @Test
    void run_withUnknownSecondArg_returnsUsageExitCode() {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[]{"payment-cluster", "--bogus"}, out, err);

        assertThat(exit).isEqualTo(64);
        assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("Usage:");
    }

    @Test
    void run_withUnknownCluster_returnsFailureExitCode(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[]{"not-a-cluster", "--dry-run"}, out, err);

        assertThat(exit).isNotZero();
        String errOut = errBytes.toString(StandardCharsets.UTF_8);
        assertThat(errOut).contains("not-a-cluster");
    }

    @Test
    void run_withVersionMismatch_returnsFailureExitCode(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        // trip-cluster is GG9; this is the GG8 test client, so resolution must fail.
        int exit = TestClientV8.run(new String[]{"trip-cluster", "--dry-run"}, out, err);

        assertThat(exit).isNotZero();
        String errOut = errBytes.toString(StandardCharsets.UTF_8);
        assertThat(errOut).containsIgnoringCase("version");
    }

    @Test
    void run_dryRunLocal_resolvesAndReturnsZero(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[]{"payment-cluster", "--dry-run"}, out, err);

        assertThat(exit).isZero();
        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(stdout)
            .contains("payment-cluster")
            .contains("LOCAL")
            .contains("localhost:10800")
            .contains("localhost:10801")
            .contains("Dry run complete");
    }

    @Test
    void run_dryRunInCluster_resolvesAndReturnsZero(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        int exit = TestClientV8.run(new String[]{"payment-cluster", "--dry-run"}, out, err);

        assertThat(exit).isZero();
        String stdout = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(stdout)
            .contains("IN_CLUSTER")
            .contains("payment-cluster-0.svc.cluster.local:10800")
            .contains("Dry run complete");
    }

    private static Path writeFixture(Path dir) throws IOException {
        String yaml = """
            schema_version: 1
            clusters:
              - name: trip-cluster
                namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  local:
                    addresses:
                      - localhost:10800
                  in_cluster:
                    addresses:
                      - trip-cluster-0.svc.cluster.local:10800
              - name: payment-cluster
                namespace: taxi-demo
                gridgain_major_version: 8
                contexts:
                  local:
                    addresses:
                      - localhost:10800
                      - localhost:10801
                  in_cluster:
                    addresses:
                      - payment-cluster-0.svc.cluster.local:10800
                      - payment-cluster-1.svc.cluster.local:10800
            """;
        Path file = dir.resolve("client-endpoints.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }
}
