package com.gridgain.demo.client;

/**
 * Thrown when {@code client-endpoints.yaml} declares a {@code schema_version}
 * that this version of the client library does not support.
 *
 * <p>The client library deliberately does NOT migrate older schemas forward;
 * that responsibility belongs to the plugin (which writes the file in the
 * latest format). If a user sees this exception it means their plugin and
 * client-utils versions are out of step and they need to upgrade one or the
 * other.</p>
 */
public class SchemaVersionMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaVersionMismatchException(int seenVersion, int expectedVersion) {
        super(buildMessage(seenVersion, expectedVersion));
    }

    private static String buildMessage(int seenVersion, int expectedVersion) {
        return "client-endpoints.yaml declares schema_version=" + seenVersion
            + " but this version of gridgain-demo-client-utils only understands schema_version="
            + expectedVersion + ". "
            + "Upgrade the client-utils dependency in your application to a version that "
            + "supports schema_version=" + seenVersion + ", or downgrade the gridgain-demo-gradle-plugin "
            + "to a version that emits schema_version=" + expectedVersion + ". "
            + "The plugin and client-utils are versioned in lock-step.";
    }
}
