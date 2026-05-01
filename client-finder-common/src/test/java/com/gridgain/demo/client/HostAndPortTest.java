package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HostAndPortTest {

    @Test
    void parsesHostnameWithPort() {
        HostAndPort hp = HostAndPort.parse("localhost:10800");
        assertThat(hp.host()).isEqualTo("localhost");
        assertThat(hp.port()).isEqualTo(10800);
    }

    @Test
    void parsesIpv4AddressWithPort() {
        HostAndPort hp = HostAndPort.parse("10.0.0.1:10800");
        assertThat(hp.host()).isEqualTo("10.0.0.1");
        assertThat(hp.port()).isEqualTo(10800);
    }

    @Test
    void parsesFullyQualifiedDomainNameWithPort() {
        HostAndPort hp = HostAndPort.parse("trip-cluster-0.trip-cluster.taxi-demo.svc.cluster.local:10800");
        assertThat(hp.host()).isEqualTo("trip-cluster-0.trip-cluster.taxi-demo.svc.cluster.local");
        assertThat(hp.port()).isEqualTo(10800);
    }

    @Test
    void splitsOnLastColon() {
        // We don't expect the plugin to emit colon-bearing hostnames, but the
        // parser must split at the LAST colon for predictable behaviour.
        HostAndPort hp = HostAndPort.parse("a:b:c:1234");
        assertThat(hp.host()).isEqualTo("a:b:c");
        assertThat(hp.port()).isEqualTo(1234);
    }

    @Test
    void throwsOnMissingColon() {
        assertThatThrownBy(() -> HostAndPort.parse("localhost"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing a ':' separator")
            .hasMessageContaining("host:port");
    }

    @Test
    void throwsOnNonIntegerPort() {
        assertThatThrownBy(() -> HostAndPort.parse("localhost:abc"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-integer port")
            .hasMessageContaining("abc");
    }

    @Test
    void throwsOnNullInput() {
        assertThatThrownBy(() -> HostAndPort.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or blank");
    }

    @Test
    void throwsOnBlankInput() {
        assertThatThrownBy(() -> HostAndPort.parse("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or blank");
    }

    @Test
    void throwsOnEmptyHost() {
        assertThatThrownBy(() -> HostAndPort.parse(":10800"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty host");
    }

    @Test
    void throwsOnEmptyPort() {
        assertThatThrownBy(() -> HostAndPort.parse("localhost:"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty port");
    }

    @Test
    void throwsOnPortOutOfRangeHigh() {
        assertThatThrownBy(() -> HostAndPort.parse("localhost:70000"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("65535");
    }

    @Test
    void throwsOnPortOutOfRangeLow() {
        assertThatThrownBy(() -> HostAndPort.parse("localhost:0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[1, 65535]");
    }

    @Test
    void toStringRoundTripsParse() {
        String s = "trip-cluster-0.svc.cluster.local:10800";
        HostAndPort hp = HostAndPort.parse(s);
        assertThat(hp.toString()).isEqualTo(s);
    }

    @Test
    void recordEqualityWorks() {
        HostAndPort a = HostAndPort.parse("localhost:10800");
        HostAndPort b = HostAndPort.parse("localhost:10800");
        HostAndPort c = HostAndPort.parse("localhost:10801");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
