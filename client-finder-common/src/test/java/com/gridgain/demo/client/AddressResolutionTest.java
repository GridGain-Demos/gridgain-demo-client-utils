package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the {@link AddressResolution} facade. These tests drive
 * the public no-arg API by setting/unsetting JVM system properties; since this
 * suite owns the JVM, that is safe.
 */
class AddressResolutionTest {

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
        // Ensure no stale state from earlier tests in the same JVM.
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
    void resolvesLocalAddressesWhenContextIsLocal(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        List<HostAndPort> addresses = AddressResolution.resolve("trip-cluster");

        assertThat(addresses).containsExactly(
            new HostAndPort("localhost", 10800),
            new HostAndPort("localhost", 10801)
        );
    }

    @Test
    void resolvesInClusterAddressesWhenContextIsInCluster(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        List<HostAndPort> addresses = AddressResolution.resolve("trip-cluster");

        assertThat(addresses).containsExactly(
            new HostAndPort("trip-cluster-0.svc.cluster.local", 10800),
            new HostAndPort("trip-cluster-1.svc.cluster.local", 10800)
        );
    }

    @Test
    void throwsNoSuchClusterWhenNameNotPresent(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        assertThatThrownBy(() -> AddressResolution.resolve("does-not-exist"))
            .isInstanceOf(NoSuchClusterException.class)
            .hasMessageContaining("'does-not-exist'")
            .hasMessageContaining("trip-cluster")
            .hasMessageContaining("payment-cluster");
    }

    @Test
    void throwsVersionMismatchWhenMajorVersionDoesNotMatch(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        // payment-cluster targets GG8; ask the facade for GG9.
        assertThatThrownBy(() -> AddressResolution.resolve("payment-cluster", 9))
            .isInstanceOf(VersionMismatchException.class)
            .hasMessageContaining("payment-cluster")
            .hasMessageContaining("gridgain_major_version=8")
            .hasMessageContaining("gridgain_major_version=9");
    }

    @Test
    void versionAwareResolvePassesWhenVersionMatches(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        List<HostAndPort> addresses = AddressResolution.resolve("trip-cluster", 9);
        assertThat(addresses).hasSize(2);
    }

    @Test
    void throwsContextAddressesUnavailableWhenLocalIsMissing(@TempDir Path tmp) throws IOException {
        // payment-cluster has no 'local' addresses block.
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        assertThatThrownBy(() -> AddressResolution.resolve("payment-cluster"))
            .isInstanceOf(ContextAddressesUnavailableException.class)
            .hasMessageContaining("'payment-cluster'")
            .hasMessageContaining("LOCAL")
            .hasMessageContaining("IN_CLUSTER")  // available context listed
            .hasMessageContaining("demo_access");
    }

    @Test
    void throwsContextAddressesUnavailableWhenLocalIsEmptyList(@TempDir Path tmp) throws IOException {
        // A degenerate case: 'local' present but with an empty addresses list.
        // Production code treats this the same as missing.
        String yaml = """
            schema_version: 1
            clusters:
              - name: trip-cluster
                namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  local:
                    addresses: []
                  in_cluster:
                    addresses:
                      - trip-cluster-0.svc.cluster.local:10800
            """;
        Path file = tmp.resolve("client-endpoints.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        assertThatThrownBy(() -> AddressResolution.resolve("trip-cluster"))
            .isInstanceOf(ContextAddressesUnavailableException.class)
            .hasMessageContaining("LOCAL");
    }

    @Test
    void parsedAddressesAreCorrectInstances(@TempDir Path tmp) throws IOException {
        Path file = writeMultiClusterFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        List<HostAndPort> addresses = AddressResolution.resolve("payment-cluster");
        assertThat(addresses).containsExactly(
            new HostAndPort("payment-cluster-0.svc.cluster.local", 10800)
        );
    }

    private static Path writeMultiClusterFixture(Path dir) throws IOException {
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
                      - localhost:10801
                  in_cluster:
                    addresses:
                      - trip-cluster-0.svc.cluster.local:10800
                      - trip-cluster-1.svc.cluster.local:10800
              - name: payment-cluster
                namespace: taxi-demo
                gridgain_major_version: 8
                contexts:
                  in_cluster:
                    addresses:
                      - payment-cluster-0.svc.cluster.local:10800
            """;
        Path file = dir.resolve("client-endpoints.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }
}
