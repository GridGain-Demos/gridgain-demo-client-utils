package com.gridgain.demo.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Public facade for the version-specific finder libraries.
 *
 * <p>Each finder library will call {@link #resolve(String, int)} (the
 * version-aware overload) to get a {@link List} of {@link HostAndPort}s
 * appropriate for the JVM's runtime context, then adapt the result into
 * whatever shape its target {@code AddressFinder} interface expects.</p>
 *
 * <p>The library is stateless and the public methods are safe to call from any
 * thread. Each call re-reads {@code client-endpoints.yaml}; callers that want
 * to avoid that overhead should cache the returned list.</p>
 */
public final class AddressResolution {

    private AddressResolution() {
        // utility class
    }

    /**
     * Resolve the address list for {@code clusterName} using the auto-detected
     * runtime context. Does NOT verify the cluster's GridGain major version;
     * use {@link #resolve(String, int)} for that.
     */
    public static List<HostAndPort> resolve(String clusterName) {
        Objects.requireNonNull(clusterName, "clusterName");
        ClientEndpoints endpoints = ClientEndpointsLoader.load(EndpointsFileLocator.locate());
        ClientEndpoints.Cluster cluster = endpoints.findCluster(clusterName);
        return resolveForCluster(cluster);
    }

    /**
     * Resolve the address list for {@code clusterName} and verify that the
     * cluster's {@code gridgain_major_version} matches
     * {@code requiredMajorVersion}. This is the entry point that the
     * gg8-client-finder and gg9-client-finder libraries will use, since each
     * is hard-wired to a single major version.
     *
     * @throws VersionMismatchException if the cluster targets a different major version
     */
    public static List<HostAndPort> resolve(String clusterName, int requiredMajorVersion) {
        Objects.requireNonNull(clusterName, "clusterName");
        ClientEndpoints endpoints = ClientEndpointsLoader.load(EndpointsFileLocator.locate());
        ClientEndpoints.Cluster cluster = endpoints.findCluster(clusterName);
        if (cluster.getGridgainMajorVersion() != requiredMajorVersion) {
            throw new VersionMismatchException(
                clusterName, cluster.getGridgainMajorVersion(), requiredMajorVersion);
        }
        return resolveForCluster(cluster);
    }

    private static List<HostAndPort> resolveForCluster(ClientEndpoints.Cluster cluster) {
        RuntimeContext ctx = RuntimeContext.detect();
        ClientEndpoints.Addresses addresses = (ctx == RuntimeContext.IN_CLUSTER)
            ? cluster.getContexts().getInCluster()
            : cluster.getContexts().getLocal();
        if (addresses == null || addresses.getAddresses().isEmpty()) {
            throw new ContextAddressesUnavailableException(
                cluster.getName(),
                ctx,
                availableContexts(cluster.getContexts())
            );
        }
        List<HostAndPort> out = new ArrayList<>(addresses.getAddresses().size());
        for (String spec : addresses.getAddresses()) {
            out.add(HostAndPort.parse(spec));
        }
        return List.copyOf(out);
    }

    private static List<RuntimeContext> availableContexts(ClientEndpoints.Contexts contexts) {
        List<RuntimeContext> out = new ArrayList<>(2);
        if (contexts.getLocal() != null && !contexts.getLocal().getAddresses().isEmpty()) {
            out.add(RuntimeContext.LOCAL);
        }
        if (contexts.getInCluster() != null && !contexts.getInCluster().getAddresses().isEmpty()) {
            out.add(RuntimeContext.IN_CLUSTER);
        }
        return out;
    }
}
