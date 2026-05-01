package com.gridgain.demo.client.gg9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gridgain.demo.client.EndpointsFileLocator;
import com.gridgain.demo.client.RuntimeContext;
import com.gridgain.demo.client.VersionMismatchException;

import java.io.IOException;
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
 * Unit tests for the GG9 {@link DemoAddressFinder} adapter. These tests do not
 * start an {@code IgniteClient}; they only verify that the adapter delegates
 * correctly to {@code AddressResolution} and reshapes the result for the
 * GG9 thin-client {@code IgniteClientAddressFinder} interface.
 */
class DemoAddressFinderTest {

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
    void rejectsNullClusterName() {
        assertThatThrownBy(() -> new DemoAddressFinder(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clusterName");
    }

    @Test
    void rejectsBlankClusterName() {
        assertThatThrownBy(() -> new DemoAddressFinder("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clusterName");
    }

    @Test
    void rejectsEmptyClusterName() {
        assertThatThrownBy(() -> new DemoAddressFinder(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("clusterName");
    }

    @Test
    void returnsAddressesForLocalContext(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "local");

        DemoAddressFinder finder = new DemoAddressFinder("trip-cluster");
        String[] addresses = finder.getAddresses();

        assertThat(addresses).containsExactly(
            "localhost:10800",
            "localhost:10801"
        );
    }

    @Test
    void returnsAddressesForInClusterContext(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        DemoAddressFinder finder = new DemoAddressFinder("trip-cluster");
        String[] addresses = finder.getAddresses();

        assertThat(addresses).containsExactly(
            "trip-cluster-0.svc.cluster.local:10800",
            "trip-cluster-1.svc.cluster.local:10800"
        );
    }

    @Test
    void returnsCorrectArrayLength(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        DemoAddressFinder finder = new DemoAddressFinder("trip-cluster");
        String[] addresses = finder.getAddresses();

        assertThat(addresses).hasSize(2);
    }

    @Test
    void rejectsClusterTargetingDifferentMajorVersion(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp);
        System.setProperty(EndpointsFileLocator.SYSTEM_PROPERTY, file.toString());
        System.setProperty(RuntimeContext.SYSTEM_PROPERTY, "in_cluster");

        // payment-cluster is GG8; this finder is GG9, so getAddresses() must throw.
        DemoAddressFinder finder = new DemoAddressFinder("payment-cluster");
        assertThatThrownBy(finder::getAddresses)
            .isInstanceOf(VersionMismatchException.class)
            .hasMessageContaining("payment-cluster")
            .hasMessageContaining("gridgain_major_version=8")
            .hasMessageContaining("gridgain_major_version=9");
    }

    /**
     * Multi-cluster fixture: one GG9 cluster and one GG8 cluster. Useful for
     * exercising both happy-path resolution and cross-version rejection from
     * the same fixture file.
     */
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
                      - localhost:10801
                  in_cluster:
                    addresses:
                      - trip-cluster-0.svc.cluster.local:10800
                      - trip-cluster-1.svc.cluster.local:10800
              - name: payment-cluster
                namespace: taxi-demo
                gridgain_major_version: 8
                contexts:
                  local:
                    addresses:
                      - localhost:10800
                  in_cluster:
                    addresses:
                      - payment-cluster-0.svc.cluster.local:10800
            """;
        Path file = dir.resolve("client-endpoints.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }
}
