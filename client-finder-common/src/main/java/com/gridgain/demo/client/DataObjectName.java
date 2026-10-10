package com.gridgain.demo.client;

import java.util.regex.Pattern;

/**
 * The name of a table or cache a test client may be asked to count.
 *
 * <p>Shared by both test clients because both take the same argument and neither should describe
 * the rule differently. On GridGain 9 the name is interpolated into
 * {@code SELECT COUNT(*) FROM <name>}; on GridGain 8 it is a cache name looked up by string. The
 * check exists for the GridGain 9 case and is applied to both so the two clients accept the same
 * arguments.</p>
 *
 * <p>This is not a security boundary. A test client runs with whatever credentials its operator
 * already has, and anyone who can pass {@code --count} can open a SQL console instead. The check
 * earns its place for a duller reason: without it, a name with a space or a quote in it comes back
 * as a SQL parse error quoting a fragment of a query the caller never typed, which reads as a
 * defect in the tool rather than as a typo in the argument.</p>
 */
public final class DataObjectName {

    /**
     * The rule, in the words an error message should use.
     *
     * <p>Held as a constant so the two clients cannot drift into describing it differently.</p>
     */
    public static final String RULE =
        "A table or cache name may contain letters, digits, underscores and hyphens, "
            + "optionally with one dot to qualify a table with its schema (for example "
            + "'PUBLIC.CUSTOMER' or 'customer-cache').";

    // One optional schema part, then the object itself. Hyphens are allowed because GridGain 8
    // cache names routinely carry them; a hyphen cannot change the meaning of the generated SQL,
    // whereas a space, quote, semicolon or parenthesis can.
    private static final Pattern ACCEPTABLE =
        Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)?");

    private DataObjectName() {
        // constants and a predicate
    }

    /**
     * Whether {@code name} may be used as a table or cache name.
     *
     * @param name the candidate, as the caller typed it
     * @return true when it is safe to pass on to the cluster
     */
    public static boolean isAcceptable(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        // Rejects a leading "--" too, which would otherwise read as a SQL comment on the GG9 path
        // and silently truncate the generated query.
        if (name.startsWith("-") || name.contains("--")) {
            return false;
        }
        return ACCEPTABLE.matcher(name).matches();
    }
}
