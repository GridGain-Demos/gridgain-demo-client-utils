package com.gridgain.demo.client.gg9;

import com.gridgain.demo.client.AddressResolution;
import com.gridgain.demo.client.HostAndPort;
import org.apache.ignite.client.IgniteClientAddressFinder;

import java.util.List;

/**
 * GG9 thin-client {@link IgniteClientAddressFinder} backed by the demo plugin's
 * {@code client-endpoints.yaml}.
 *
 * <p>Resolves the cluster's currently-deployed addresses each time
 * {@link #getAddresses()} is called, so a redeploy that changes addresses is
 * picked up without recompiling the client app.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IgniteClient client = IgniteClient.builder()
 *     .addressFinder(new DemoAddressFinder("trip-cluster"))
 *     .build();
 * }</pre>
 *
 * <p>Note: in GridGain 9 the relevant interface is the top-level
 * {@code org.apache.ignite.client.IgniteClientAddressFinder} (returning
 * {@code String[]}), wired in via
 * {@code IgniteClient.Builder.addressFinder(...)}.</p>
 */
public final class DemoAddressFinder implements IgniteClientAddressFinder {

    private static final int GG_MAJOR_VERSION = 9;

    private final String clusterName;

    /**
     * @param clusterName the {@code clusters[].name} entry in
     *     {@code client-endpoints.yaml} that this finder should resolve. Must
     *     be non-null and non-blank.
     * @throws IllegalArgumentException if {@code clusterName} is null or blank
     */
    public DemoAddressFinder(String clusterName) {
        if (clusterName == null || clusterName.isBlank()) {
            throw new IllegalArgumentException(
                "clusterName must be non-null and non-blank. "
                    + "Pass the name of the cluster as it appears in the "
                    + "'clusters[].name' field of client-endpoints.yaml.");
        }
        this.clusterName = clusterName;
    }

    @Override
    public String[] getAddresses() {
        List<HostAndPort> addrs = AddressResolution.resolve(clusterName, GG_MAJOR_VERSION);
        return addrs.stream()
            .map(hp -> hp.host() + ":" + hp.port())
            .toArray(String[]::new);
    }
}
