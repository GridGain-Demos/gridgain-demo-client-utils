package com.gridgain.demo.client.gg8;

import com.gridgain.demo.client.RuntimeContext;

import java.io.PrintStream;

import org.apache.ignite.Ignition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;

/**
 * Standalone runnable client that exercises the GG8 {@link DemoAddressFinder}
 * against a live cluster: resolves addresses, opens a thin client, prints a
 * connectivity probe, and exits.
 *
 * <p>Usage: {@code java -cp <classpath> com.gridgain.demo.client.gg8.TestClientV8 <clusterName> [--dry-run]}</p>
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
 */
public final class TestClientV8 {

    private TestClientV8() {
        // entry point class
    }

    /**
     * CLI entry point. Delegates to {@link #run(String[], PrintStream, PrintStream)} and exits.
     *
     * @param args command-line arguments: {@code <clusterName> [--dry-run]}
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
        if (args.length < 1 || args.length > 2) {
            err.println("Usage: TestClientV8 <clusterName> [--dry-run]");
            return 64;
        }
        String clusterName = args[0];
        boolean dryRun = args.length == 2 && "--dry-run".equals(args[1]);
        if (args.length == 2 && !dryRun) {
            err.println("Usage: TestClientV8 <clusterName> [--dry-run]");
            return 64;
        }

        try {
            out.println("=== TestClientV8 ===");
            out.println("Cluster name : " + clusterName);
            out.println("Runtime ctx  : " + RuntimeContext.detect());
            out.println("Dry run      : " + dryRun);

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
            if (reachable < addresses.length) {
                out.println("Some server nodes unreachable.");
                return 1;
            }
            out.println("All server nodes reachable.");
            return 0;
        } catch (Exception e) {
            err.println("TestClientV8 failed: " + e.getMessage());
            e.printStackTrace(err);
            return 1;
        }
    }
}
