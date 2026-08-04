package com.gridgain.demo.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses {@code client-endpoints.yaml} into a {@link ClientEndpoints} graph.
 *
 * <p>Uses SnakeYAML 1.33's {@link SafeConstructor} to reject arbitrary tagged
 * objects — only standard YAML scalar/list/map types are allowed.</p>
 *
 * <p>The expected schema version is {@value #EXPECTED_SCHEMA_VERSION}. A
 * mismatch throws {@link SchemaVersionMismatchException} (no silent
 * migration). The version is what makes a plugin/client-utils skew report itself
 * plainly: a library expecting version 1 refuses a version 2 file outright
 * rather than reporting a missing {@code namespace} on a host cluster that
 * never had one.</p>
 */
public final class ClientEndpointsLoader {

    /**
     * The only schema version this client library understands.
     *
     * <p>Version 2 introduced the required {@code deployment_kind} discriminator and made
     * {@code namespace} and {@code contexts.in_cluster} specific to Kubernetes clusters, so that a
     * cluster deployed to a collection of machines is described in its own terms rather than as a
     * Kubernetes cluster missing fields.</p>
     */
    public static final int EXPECTED_SCHEMA_VERSION = 2;

    private ClientEndpointsLoader() {
        // utility class
    }

    public static ClientEndpoints load(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file path must not be null");
        }
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException(
                "client-endpoints.yaml at '" + file + "' is not readable. "
                    + "Verify the file exists and the JVM has read permission.");
        }
        Object loaded;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            loaded = yaml.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read client-endpoints.yaml from '" + file + "'. "
                    + "Verify the file is valid UTF-8 YAML and is not in use.", e);
        } catch (RuntimeException e) {
            // SnakeYAML throws subclasses of YAMLException (a RuntimeException).
            // Wrap with a remediation hint while preserving the underlying cause.
            throw new IllegalArgumentException(
                "client-endpoints.yaml at '" + file + "' is not valid YAML: " + e.getMessage()
                    + ". Re-run the plugin deploy task to regenerate the file.", e);
        }

        if (loaded == null) {
            throw new IllegalArgumentException(
                "client-endpoints.yaml at '" + file + "' is empty. "
                    + "Re-run the plugin deploy task to regenerate it.");
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException(
                "client-endpoints.yaml at '" + file + "' must contain a YAML mapping at the top level, but got "
                    + loaded.getClass().getSimpleName() + ". "
                    + "Re-run the plugin deploy task to regenerate the file.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) loaded;

        ClientEndpoints endpoints = ClientEndpoints.fromMap(rootMap);
        if (endpoints.getSchemaVersion() != EXPECTED_SCHEMA_VERSION) {
            throw new SchemaVersionMismatchException(endpoints.getSchemaVersion(), EXPECTED_SCHEMA_VERSION);
        }
        return endpoints;
    }
}
