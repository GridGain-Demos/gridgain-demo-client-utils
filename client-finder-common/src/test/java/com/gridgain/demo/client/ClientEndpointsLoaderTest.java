package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientEndpointsLoaderTest {

    @Test
    void loadsValidYaml(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
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
            """);

        ClientEndpoints endpoints = ClientEndpointsLoader.load(file);

        assertThat(endpoints.getSchemaVersion()).isEqualTo(1);
        assertThat(endpoints.getClusters()).hasSize(1);
        ClientEndpoints.Cluster cluster = endpoints.getClusters().get(0);
        assertThat(cluster.getName()).isEqualTo("trip-cluster");
        assertThat(cluster.getNamespace()).isEqualTo("taxi-demo");
        assertThat(cluster.getGridgainMajorVersion()).isEqualTo(9);
        assertThat(cluster.getContexts().getLocal().getAddresses())
            .containsExactly("localhost:10800", "localhost:10801");
        assertThat(cluster.getContexts().getInCluster().getAddresses())
            .containsExactly("trip-cluster-0.svc.cluster.local:10800",
                "trip-cluster-1.svc.cluster.local:10800");
    }

    @Test
    void allowsLocalContextToBeOmitted(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            schema_version: 1
            clusters:
              - name: backend-cluster
                namespace: taxi-demo
                gridgain_major_version: 8
                contexts:
                  in_cluster:
                    addresses:
                      - backend-0.svc.cluster.local:10800
            """);

        ClientEndpoints endpoints = ClientEndpointsLoader.load(file);

        ClientEndpoints.Cluster cluster = endpoints.findCluster("backend-cluster");
        assertThat(cluster.getContexts().getLocal()).isNull();
        assertThat(cluster.getContexts().getInCluster().getAddresses())
            .containsExactly("backend-0.svc.cluster.local:10800");
    }

    @Test
    void throwsSchemaVersionMismatchOnVersion2(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            schema_version: 2
            clusters:
              - name: trip-cluster
                namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  in_cluster:
                    addresses:
                      - trip-cluster-0.svc.cluster.local:10800
            """);

        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(SchemaVersionMismatchException.class)
            .hasMessageContaining("schema_version=2")
            .hasMessageContaining("schema_version=1")
            .hasMessageContaining("lock-step");
    }

    @Test
    void rejectsMalformedYaml(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("client-endpoints.yaml");
        // The colon followed by a quote with no closing quote is unambiguously broken.
        Files.writeString(file, "schema_version: 1\nclusters: [\nname: \"unterminated\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not valid YAML");
    }

    @Test
    void rejectsEmptyFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("client-endpoints.yaml");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is empty");
    }

    @Test
    void rejectsMissingSchemaVersion(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            clusters:
              - name: x
                namespace: y
                gridgain_major_version: 9
                contexts:
                  in_cluster:
                    addresses:
                      - foo:1
            """);

        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schema_version");
    }

    @Test
    void rejectsMissingClusterName(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            schema_version: 1
            clusters:
              - namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  in_cluster:
                    addresses:
                      - foo:1
            """);

        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'name'")
            .hasMessageContaining("clusters[0]");
    }

    @Test
    void rejectsNonReadableFile(@TempDir Path tmp) {
        Path file = tmp.resolve("does-not-exist.yaml");
        assertThatThrownBy(() -> ClientEndpointsLoader.load(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not readable");
    }

    @Test
    void multipleClustersFindsCorrectOne(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            schema_version: 1
            clusters:
              - name: trip-cluster
                namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  in_cluster:
                    addresses:
                      - a:1
              - name: payment-cluster
                namespace: taxi-demo
                gridgain_major_version: 8
                contexts:
                  in_cluster:
                    addresses:
                      - b:2
            """);

        ClientEndpoints endpoints = ClientEndpointsLoader.load(file);
        assertThat(endpoints.findCluster("trip-cluster").getGridgainMajorVersion()).isEqualTo(9);
        assertThat(endpoints.findCluster("payment-cluster").getGridgainMajorVersion()).isEqualTo(8);
    }

    @Test
    void findClusterThrowsWithAvailableNames(@TempDir Path tmp) throws IOException {
        Path file = writeFixture(tmp, """
            schema_version: 1
            clusters:
              - name: trip-cluster
                namespace: taxi-demo
                gridgain_major_version: 9
                contexts:
                  in_cluster:
                    addresses:
                      - a:1
            """);

        ClientEndpoints endpoints = ClientEndpointsLoader.load(file);
        assertThatThrownBy(() -> endpoints.findCluster("nope"))
            .isInstanceOf(NoSuchClusterException.class)
            .hasMessageContaining("'nope'")
            .hasMessageContaining("trip-cluster");
    }

    private static Path writeFixture(Path dir, String content) throws IOException {
        Path file = dir.resolve("client-endpoints.yaml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
