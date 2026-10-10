package com.gridgain.demo.client.gg9;

import com.gridgain.demo.client.DataObjectName;
import com.gridgain.demo.client.RuntimeContext;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.table.Table;
import org.apache.ignite.tx.Transaction;

/**
 * Standalone runnable client that exercises the GG9 {@link DemoAddressFinder}
 * against a live cluster: resolves addresses, opens a thin client, prints a
 * connectivity probe, optionally counts a table's rows, and exits.
 *
 * <p>Usage: {@code java -cp <classpath> com.gridgain.demo.client.gg9.TestClientV9 <clusterName> [--dry-run] [--count <table>]}</p>
 *
 * <p>The {@code --dry-run} flag stops after address resolution and skips the
 * actual {@code IgniteClient.builder().build()} call. This is useful in
 * air-gapped environments where users want to verify their
 * {@code client-endpoints.yaml} resolution without forcing a connection attempt.</p>
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
 * replication, where the whole question is whether rows written to one cluster appear on another.
 * {@code --count <table>} runs {@code SELECT COUNT(*)} over a pooled client and prints the number,
 * so the same command against each end of a replication pair is the comparison.</p>
 *
 * <p>It counts rows and nothing more. Equal counts on two clusters are not proof that the contents
 * match, and a count taken while a load generator is still running is a moving number on both
 * sides. For replication the usable shape is: stop the load, let it settle, then count each end.</p>
 */
public final class TestClientV9 {

    private static final String USAGE =
        "Usage: TestClientV9 <clusterName> [--dry-run] [--count <table>]";

    private TestClientV9() {
        // entry point class
    }

    /**
     * CLI entry point. Delegates to {@link #run(String[], PrintStream, PrintStream)} and exits.
     *
     * @param args command-line arguments: {@code <clusterName> [--dry-run] [--count <table>]}
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
        String countTable = "";

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if ("--count".equals(arg)) {
                if (i + 1 >= args.length) {
                    err.println("--count needs the name of a table to count. " + USAGE);
                    return 64;
                }
                countTable = args[++i];
            } else {
                err.println(USAGE);
                return 64;
            }
        }

        if (dryRun && !countTable.isEmpty()) {
            // Refused rather than ignored: a dry run opens no connection, so a count it accepted
            // and skipped would print nothing where the caller is expecting a number.
            err.println("--dry-run and --count cannot be combined: a dry run stops before opening "
                + "a connection, so there is nothing to count. Drop --dry-run to count rows, or "
                + "drop --count to check address resolution only.");
            return 64;
        }
        if (!countTable.isEmpty() && !DataObjectName.isAcceptable(countTable)) {
            err.println("Cannot count '" + countTable + "'. " + DataObjectName.RULE);
            return 64;
        }

        try {
            out.println("=== TestClientV9 ===");
            out.println("Cluster name : " + clusterName);
            out.println("Runtime ctx  : " + RuntimeContext.detect());
            out.println("Dry run      : " + dryRun);
            if (!countTable.isEmpty()) {
                out.println("Count table  : " + countTable);
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
                try (IgniteClient nodeClient = IgniteClient.builder().addresses(address).build()) {
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

            if (countTable.isEmpty()) {
                return allReachable ? 0 : 1;
            }

            // Attempted even when a node was unreachable: a degraded cluster still answers, and
            // somebody counting rows during an incident wants the number more than they want a
            // tidy exit code. Both outcomes are folded into the exit status below.
            boolean counted = countRows(addresses, countTable, out, err);
            return allReachable && counted ? 0 : 1;
        } catch (Exception e) {
            err.println("TestClientV9 failed: " + e.getMessage());
            e.printStackTrace(err);
            return 1;
        }
    }

    /**
     * Counts the rows of one table over a client pooled across every resolved address.
     *
     * <p>Pooled rather than per-node, unlike the reachability probe above: a count is a
     * cluster-wide question, and this is the shape an application's own client has.</p>
     *
     * @return true when the count was printed, false when it could not be taken
     */
    private static boolean countRows(String[] addresses, String table, PrintStream out, PrintStream err) {
        out.println();
        out.println("=== Row count ===");
        try (IgniteClient client = IgniteClient.builder().addresses(addresses).build()) {
            try (ResultSet<SqlRow> rs =
                     client.sql().execute((Transaction) null, "SELECT COUNT(*) FROM " + table)) {
                if (!rs.hasNext()) {
                    // No row back from a COUNT(*) is not something GridGain does; if it ever
                    // happens, saying so beats printing a zero that was never measured.
                    err.println("Counting '" + table + "' returned no result row. This should not "
                        + "happen for a COUNT(*) — treat the cluster as suspect rather than the "
                        + "table as empty.");
                    return false;
                }
                out.println("  " + table + " : " + rs.next().longValue(0) + " rows");
                return true;
            }
        } catch (Exception e) {
            err.println("Could not count '" + table + "' on this cluster: " + e.getMessage());
            err.println(availableTables(addresses));
            return false;
        }
    }

    /**
     * The tables that do exist, for the error path.
     *
     * <p>A missing table is the likeliest reason a count fails, and on GridGain 9 the usual cause is
     * case: an unquoted identifier in DDL is folded to upper case, so a table created as
     * {@code customer} is {@code CUSTOMER} here. Listing the real names settles that in one line
     * instead of a round of guessing.</p>
     */
    private static String availableTables(String[] addresses) {
        try (IgniteClient client = IgniteClient.builder().addresses(addresses).build()) {
            List<Table> tables = client.tables().tables();
            if (tables.isEmpty()) {
                return "This cluster has no tables at all. If replication was meant to create them, "
                    + "note that GridGain 9 does not propagate schema — the tables must be created "
                    + "on both clusters before replication starts.";
            }
            String names = tables.stream()
                .map(t -> t.qualifiedName().toCanonicalForm())
                .sorted()
                .collect(Collectors.joining(", "));
            return "Tables on this cluster: " + names
                + ". Names are case-sensitive here; unquoted DDL identifiers are stored upper-case.";
        } catch (Exception e) {
            return "Could not list this cluster's tables either: " + e.getMessage();
        }
    }
}
