package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link DataObjectName} — what may be named as a table or cache to count.
 *
 * <p>The name a caller supplies is interpolated into a {@code SELECT COUNT(*) FROM ...} on the
 * GridGain 9 path, so it is checked before it ever reaches the cluster. The point is not to defend
 * a diagnostic tool against its own operator: it is that an unchecked name comes back as a SQL
 * parse error quoting a fragment of a query the caller never wrote, which reads as a bug in the
 * tool rather than as a typo in the argument.</p>
 */
class DataObjectNameTest {

    @Test
    void plainIdentifierIsAccepted() {
        assertThat(DataObjectName.isAcceptable("CUSTOMER")).isTrue();
        assertThat(DataObjectName.isAcceptable("customer_accounts")).isTrue();
        assertThat(DataObjectName.isAcceptable("T1")).isTrue();
    }

    @Test
    void schemaQualifiedNameIsAccepted() {
        // The shape GridGain 9 tables are usually written in, and the one the generator's DDL emits.
        assertThat(DataObjectName.isAcceptable("PUBLIC.CUSTOMER")).isTrue();
    }

    @Test
    void gridGain8CacheNamingIsAccepted() {
        // GG8 cache names are freer than SQL identifiers — the generator produces hyphenated ones,
        // and a cache name is never interpolated into SQL, so it is only checked for the characters
        // that would make a shell or a log line ambiguous.
        assertThat(DataObjectName.isAcceptable("customer-cache")).isTrue();
        assertThat(DataObjectName.isAcceptable("ignite-sys-cache")).isTrue();
    }

    @Test
    void anythingThatCouldChangeTheQueryIsRejected() {
        assertThat(DataObjectName.isAcceptable("CUSTOMER; DROP TABLE ACCOUNT")).isFalse();
        assertThat(DataObjectName.isAcceptable("CUSTOMER WHERE 1=1")).isFalse();
        assertThat(DataObjectName.isAcceptable("CUSTOMER'")).isFalse();
        assertThat(DataObjectName.isAcceptable("(SELECT 1)")).isFalse();
        assertThat(DataObjectName.isAcceptable("A--B")).isFalse();
    }

    @Test
    void emptyAndBlankAreRejected() {
        assertThat(DataObjectName.isAcceptable("")).isFalse();
        assertThat(DataObjectName.isAcceptable("   ")).isFalse();
    }

    @Test
    void theRuleIsStatedInWordsForErrorMessages() {
        // The message is the whole reason this is a named class rather than a regex at two call
        // sites: a caller who trips the check has to be told what IS allowed.
        assertThat(DataObjectName.RULE)
            .contains("letters")
            .contains("digits");
    }
}
