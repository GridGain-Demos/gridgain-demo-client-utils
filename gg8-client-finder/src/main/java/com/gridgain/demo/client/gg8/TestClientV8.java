package com.gridgain.demo.client.gg8;

import com.gridgain.demo.client.DataObjectName;
import com.gridgain.demo.client.RuntimeContext;

import java.io.PrintStream;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;

/**
 * Standalone runnable client that exercises the GG8 {@link DemoAddressFinder}
 * against a live cluster: resolves addresses, opens a thin client, prints a
 * connectivity probe, optionally counts a cache's entries, and exits.
 *
 * <p>Usage: {@code java -cp <classpath> com.gridgain.demo.client.gg8.TestClientV8 <clusterName> [--dry-run] [--count <cache>]}</p>
 *
 * <p>The {@code --dry-run} flag stops after address resolution and skips the
 * actual {@code Ignition.startClient(...)} call. This is useful in air-gapped
 * environments where users want to verify their {@code client-endpoints.yaml}
 * resolution without forcing a connection attempt.</p>
 *
 * <p>Connectivity is verified per-node: the test opens a single-address client
 * against each resolved address and reports OK or FAILED individually. The
 * exit code is non-zero if any node is unreachable. This is more meaningful
 * than a single pooled connection because partition-aware clients route reads
 * and writes to specific nodes — if even one node is unreachable, application
 * traffic to that partition will fail.</p>
 *
 * <h2>{@code --count}, and what it is for</h2>
 *
 * <p>Reachability answers "is the cluster up". It does not answer "did the data arrive", and
 * nothing else in the toolkit did either — which is what blocked an end-to-end test of data centre
 * replication, where the whole question is whether entries written to one cluster appear on
 * another. {@code --count <cache>} prints the cache's entry count, so the same command against each
 * end of a replication pair is the comparison.</p>
 *
 * <p>A <strong>cache</strong>, not a table: GridGain 8 replication operates on caches, and the data
 * generator's GridGain 8 target writes key-value entries rather than SQL rows. The GridGain 9
 * client counts tables instead, which is the same argument meaning the thing each version
 * replicates.</p>
 */
public final class TestClientV8 {

    private static final String USAGE =
        "Usage: TestClientV8 <clusterName> [--dry-run] [--count <cache>]";

    private TestClientV8() {
        // entry point class
    }

    /**
     * CLI entry point. Delegates to {@link #run(String[], PrintStream, PrintStream)} and exits.
     *
     * @param args command-line arguments: {@code <clusterName> [--dry-run] [--count <cache>]}
     */
    public static void main(String[] args) {
        int exit = run(args, System.out, System.err);
        System.exit(exit);
    }

    /**
     * Package-private testable entry point. Returns an exit code instead of
     * calling {@link System#exit(int)} so unit tests can drive it directly.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 1) {
            err.println(USAGE);
            return 64;
        }
        String clusterName = args[0];
        boolean dryRun = false;
        String countCache = "";

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if ("--count".equals(arg)) {
                if (i + 1 >= args.length) {
                    err.println("--count needs the name of a cache to count. " + USAGE);
                    return 64;
                }
                countCache = args[++i];
            } else {
                err.println(USAGE);
                return 64;
            }
        }

        if (dryRun && !countCache.isEmpty()) {
            err.println("--dry-run and --count cannot be combined: a dry run stops before opening "
                + "a connection, so there is nothing to count. Drop --dry-run to count entries, or "
                + "drop --count to check address resolution only.");
            return 64;
        }
        if (!countCache.isEmpty() && !DataObjectName.isAcceptable(countCache)) {
            err.println("Cannot count '" + countCache + "'. " + DataObjectName.RULE);
            return 64;
        }

        try {
            out.println("=== TestClientV8 ===");
            out.println("Cluster name : " + clusterName);
            out.println("Runtime ctx  : " + RuntimeContext.detect());
            out.println("Dry run      : " + dryRun);
            if (!countCache.isEmpty()) {
                out.println("Count cache  : " + countCache);
            }

            DemoAddressFinder finder = new DemoAddressFinder(clusterName);
            String[] addresses = finder.getAddresses();
            out.println("Resolved addresses (" + addresses.length + "):");
            for (String addr : addresses) {
                out.println("  " + addr);
            }

            if (dryRun) {
                out.println("Dry run complete - skipping connection attempts.");
                return 0;
            }

            out.println();
            out.println("=== Per-node reachability ===");
            int reachable = 0;
            for (String address : addresses) {
                out.print("  " + address + " ... ");
                out.flush();
                ClientConfiguration cfg = new ClientConfiguration().setAddresses(address);
                try (IgniteClient nodeClient = Ignition.startClient(cfg)) {
                    out.println("OK");
                    reachable++;
                } catch (Exception e) {
                    out.println("FAILED: " + e.getMessage());
                }
            }
            out.println();
            out.println("Reachable: " + reachable + "/" + addresses.length + " server nodes.");
            boolean allReachable = reachable == addresses.length;
            out.println(allReachable
                ? "All server nodes reachable."
                : "Some server nodes unreachable.");

            if (countCache.isEmpty()) {
                return allReachable ? 0 : 1;
            }

            // Attempted even when a node was unreachable — see the GG9 client for why.
            boolean counted = countEntries(addresses, countCache, out, err);
            return allReachable && counted ? 0 : 1;
        } catch (Exception e) {
            err.println("TestClientV8 failed: " + e.getMessage());
            e.printStackTrace(err);
            return 1;
        }
    }

    /**
     * Counts one cache's entries over a client pooled across every resolved address.
     *
     * <p>{@link CachePeekMode#PRIMARY} explicitly, rather than the default: a bare
     * {@code size()} counts every copy an entry has, so on a cache with one backup it returns
     * roughly twice the number of distinct entries. Comparing that against the other end of a
     * replication pair whose backup count differs would show a mismatch that is not there.</p>
     *
     * @return true when the count was printed, false when it could not be taken
     */
    private static boolean countEntries(String[] addresses, String cacheName, PrintStream out, PrintStream err) {
        out.println();
        out.println("=== Entry count ===");
        ClientConfiguration cfg = new ClientConfiguration().setAddresses(addresses);
        try (IgniteClient client = Ignition.startClient(cfg)) {
            Collection<String> names = client.cacheNames();
            if (!names.contains(cacheName)) {
                // Checked rather than left to fail on the size() call: GG8 hands back a cache
                // handle for a name that does not exist, and the failure surfaces later and less
                // clearly. Cache names are case-sensitive, which is the usual cause.
                err.println("This cluster has no cache named '" + cacheName + "'.");
                err.println(availableCaches(names));
                return false;
            }
            ClientCache<Object, Object> cache = client.cache(cacheName);
            out.println("  " + cacheName + " : " + cache.size(CachePeekMode.PRIMARY) + " entries");
            return true;
        } catch (Exception e) {
            err.println("Could not count '" + cacheName + "' on this cluster: " + e.getMessage());
            return false;
        }
    }

    /**
     * The caches that do exist, for the error path.
     *
     * <p>Names are case-sensitive on GridGain 8, and the system caches are listed alongside the
     * demo's own, so seeing the real list settles a mistyped name in one line.</p>
     */
    private static String availableCaches(Collection<String> names) {
        if (names.isEmpty()) {
            return "This cluster has no caches at all. If replication was meant to create them, "
                + "note that the receiving cluster needs the cache to exist before entries can "
                + "land in it.";
        }
        return "Caches on this cluster: "
            + names.stream().sorted().collect(Collectors.joining(", "))
            + ". Names are case-sensitive.";
    }
}
