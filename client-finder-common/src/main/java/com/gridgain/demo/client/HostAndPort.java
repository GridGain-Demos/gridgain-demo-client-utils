package com.gridgain.demo.client;

/**
 * Immutable representation of a {@code host:port} pair.
 *
 * <p>This is intentionally a neutral type: the GG8 finder needs
 * {@link java.net.InetSocketAddress}, the GG9 finder needs raw {@code String[]},
 * and they will each adapt this type into their target shape. We do not
 * use {@link java.net.InetSocketAddress} here because that class triggers DNS
 * lookups eagerly in some constructors and we want the common layer to stay
 * lookup-free.</p>
 *
 * @param host the hostname or IP literal (never {@code null}, never empty)
 * @param port a TCP port in the range {@code [1, 65535]}
 */
public record HostAndPort(String host, int port) {

    public HostAndPort {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                "Host must be non-null and non-blank. Provide a hostname or IP literal " +
                    "(for example 'localhost' or '10.0.0.1').");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                "Port " + port + " is outside the valid TCP range [1, 65535]. " +
                    "Check the addresses entry for this cluster in client-endpoints.yaml.");
        }
    }

    /**
     * Parse a {@code host:port} string. The split happens at the <em>last</em>
     * colon so that bare IPv6 literals (without brackets) would still be
     * rejected by the port-parsing step rather than producing a silently wrong
     * split. The plugin only emits hostnames and IPv4 addresses today, so
     * IPv6 literals are out of scope; if they appear we throw.
     *
     * @param spec a string of the form {@code host:port}
     * @return the parsed {@link HostAndPort}
     * @throws IllegalArgumentException if {@code spec} is null, missing a
     *     colon, has an empty host, or has a non-integer / out-of-range port
     */
    public static HostAndPort parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException(
                "Cannot parse a null or blank address. Expected 'host:port'.");
        }
        int idx = spec.lastIndexOf(':');
        if (idx < 0) {
            throw new IllegalArgumentException(
                "Address '" + spec + "' is missing a ':' separator. " +
                    "Expected format is 'host:port' (for example 'localhost:10800').");
        }
        String host = spec.substring(0, idx);
        String portStr = spec.substring(idx + 1);
        if (host.isBlank()) {
            throw new IllegalArgumentException(
                "Address '" + spec + "' has an empty host. " +
                    "Expected format is 'host:port' (for example 'localhost:10800').");
        }
        if (portStr.isBlank()) {
            throw new IllegalArgumentException(
                "Address '" + spec + "' has an empty port. " +
                    "Expected format is 'host:port' (for example 'localhost:10800').");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Address '" + spec + "' has a non-integer port '" + portStr + "'. " +
                    "Expected format is 'host:port' (for example 'localhost:10800').", e);
        }
        return new HostAndPort(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
