package com.gridgain.demo.client;

/**
 * Thrown by version-specific finders when the cluster they are wired into
 * declares a different GridGain major version than the finder targets.
 *
 * <p>For example, the {@code gg9-client-finder} will throw this if it is
 * pointed at a cluster whose {@code gridgain_major_version} is {@code 8}.
 * That mismatch is virtually always a misconfiguration in the user's
 * application.</p>
 */
public class VersionMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VersionMismatchException(String clusterName, int actualMajorVersion, int requiredMajorVersion) {
        super(buildMessage(clusterName, actualMajorVersion, requiredMajorVersion));
    }

    private static String buildMessage(String clusterName, int actualMajorVersion, int requiredMajorVersion) {
        return "Cluster '" + clusterName + "' declares gridgain_major_version=" + actualMajorVersion
            + " but the calling finder requires gridgain_major_version=" + requiredMajorVersion + ". "
            + "Use the gg" + actualMajorVersion + "-client-finder library for this cluster, "
            + "or point your application at a cluster that targets GridGain " + requiredMajorVersion + ".";
    }
}
