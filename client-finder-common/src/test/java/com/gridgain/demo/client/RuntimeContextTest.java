package com.gridgain.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RuntimeContextTest {

    @Test
    void localWhenNoOverridesAndNotInPod() {
        Map<String, String> sysProps = Map.of();
        Map<String, String> env = Map.of();
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(env))).isEqualTo(RuntimeContext.LOCAL);
    }

    @Test
    void inClusterWhenKubernetesServiceHostIsSet() {
        Map<String, String> env = Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1");
        assertThat(RuntimeContext.detect(lookupOf(Map.of()), lookupOf(env))).isEqualTo(RuntimeContext.IN_CLUSTER);
    }

    @Test
    void localWhenKubernetesServiceHostIsBlank() {
        // A blank value is treated as "not set" (no autodetect signal).
        Map<String, String> env = new HashMap<>();
        env.put("KUBERNETES_SERVICE_HOST", "   ");
        assertThat(RuntimeContext.detect(lookupOf(Map.of()), lookupOf(env))).isEqualTo(RuntimeContext.LOCAL);
    }

    @Test
    void systemPropertyOverrideWinsOverK8sAutodetect() {
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "local");
        Map<String, String> env = Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1");
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(env))).isEqualTo(RuntimeContext.LOCAL);
    }

    @Test
    void systemPropertyOverrideCanForceInCluster() {
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "in_cluster");
        Map<String, String> env = Map.of();
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(env))).isEqualTo(RuntimeContext.IN_CLUSTER);
    }

    @Test
    void envVarOverrideWinsOverK8sAutodetect() {
        Map<String, String> env = Map.of(
            "GG_DEMO_CLIENT_CONTEXT", "local",
            "KUBERNETES_SERVICE_HOST", "10.0.0.1"
        );
        assertThat(RuntimeContext.detect(lookupOf(Map.of()), lookupOf(env))).isEqualTo(RuntimeContext.LOCAL);
    }

    @Test
    void systemPropertyWinsOverEnvVarOverride() {
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "local");
        Map<String, String> env = Map.of("GG_DEMO_CLIENT_CONTEXT", "in_cluster");
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(env))).isEqualTo(RuntimeContext.LOCAL);
    }

    @Test
    void overrideValueIsCaseInsensitive() {
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "IN_CLUSTER");
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(Map.of())))
            .isEqualTo(RuntimeContext.IN_CLUSTER);
    }

    @Test
    void invalidSystemPropertyThrows() {
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "bogus");
        assertThatThrownBy(() -> RuntimeContext.detect(lookupOf(sysProps), lookupOf(Map.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'bogus'")
            .hasMessageContaining("local")
            .hasMessageContaining("in_cluster");
    }

    @Test
    void invalidEnvVarThrows() {
        Map<String, String> env = Map.of("GG_DEMO_CLIENT_CONTEXT", "production");
        assertThatThrownBy(() -> RuntimeContext.detect(lookupOf(Map.of()), lookupOf(env)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'production'")
            .hasMessageContaining("GG_DEMO_CLIENT_CONTEXT");
    }

    @Test
    void blankSystemPropertyFallsThroughToEnvVar() {
        // Blank-only override = "not set" semantically.
        Map<String, String> sysProps = Map.of("gg.demo.client.context", "   ");
        Map<String, String> env = Map.of("GG_DEMO_CLIENT_CONTEXT", "in_cluster");
        assertThat(RuntimeContext.detect(lookupOf(sysProps), lookupOf(env))).isEqualTo(RuntimeContext.IN_CLUSTER);
    }

    @Test
    void blankEnvVarFallsThroughToAutodetect() {
        Map<String, String> env = new HashMap<>();
        env.put("GG_DEMO_CLIENT_CONTEXT", "   ");
        env.put("KUBERNETES_SERVICE_HOST", "10.0.0.1");
        assertThat(RuntimeContext.detect(lookupOf(Map.of()), lookupOf(env))).isEqualTo(RuntimeContext.IN_CLUSTER);
    }

    private static Function<String, String> lookupOf(Map<String, String> backing) {
        return backing::get;
    }
}
