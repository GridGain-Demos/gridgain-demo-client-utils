package com.gridgain.demo.client.gg8;

import com.gridgain.demo.client.AddressResolution;
import com.gridgain.demo.client.HostAndPort;
import org.apache.ignite.client.ClientAddressFinder;

import java.util.List;

/**
 * GG8 thin-client {@link ClientAddressFinder} backed by the demo plugin's
 * {@code client-endpoints.yaml}.
 *
 * <p>Resolves the cluster's currently-deployed addresses each time
 * {@link #getAddresses()} is called, so a redeploy that changes addresses is
 * picked up without recompiling the client app.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * ClientConfiguration cfg = new ClientConfiguration()
 *     .setAddressesFinder(new DemoAddressFinder("trip-cluster"));
 * IgniteClient client = Ignition.startClient(cfg);
 * }</pre>
 *
 * <p>Note: the relevant interface in GridGain 8 thin-client is
 * {@link org.apache.ignite.client.ClientAddressFinder} (returning
 * {@code String[]}), not the server-side TCP discovery SPI's
 * {@code org.apache.ignite.configuration.AddressFinder}.</p>
 */
public final class DemoAddressFinder implements ClientAddressFinder {

    private static final int GG_MAJOR_VERSION = 8;

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
