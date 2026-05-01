package com.gridgain.demo.client;

import java.util.Locale;
import java.util.function.Function;

/**
 * Whether the calling JVM is running on a developer laptop ({@link #LOCAL}) or
 * inside a Kubernetes pod ({@link #IN_CLUSTER}). The two contexts need
 * different host/port lists because a {@code *.svc.cluster.local} hostname is
 * not resolvable from a laptop, and a {@code localhost} forward is not
 * resolvable from inside a pod.
 *
 * <p>Detection order:</p>
 * <ol>
 *   <li>System property {@value #SYSTEM_PROPERTY} (values {@code local} /
 *       {@code in_cluster}, case-insensitive).</li>
 *   <li>Environment variable {@value #ENV_VAR} (same values).</li>
 *   <li>Auto-detect: {@link #IN_CLUSTER} if env var
 *       {@value #KUBERNETES_SERVICE_HOST_ENV} is set and non-empty;
 *       otherwise {@link #LOCAL}.</li>
 * </ol>
 *
 * <p>An invalid override value (anything other than {@code local} or
 * {@code in_cluster}, ignoring case) throws {@link IllegalStateException}.</p>
 */
public enum RuntimeContext {
    LOCAL,
    IN_CLUSTER;

    public static final String SYSTEM_PROPERTY = "gg.demo.client.context";
    public static final String ENV_VAR = "GG_DEMO_CLIENT_CONTEXT";
    public static final String KUBERNETES_SERVICE_HOST_ENV = "KUBERNETES_SERVICE_HOST";

    /** Public production API: detects context against the live JVM environment. */
    public static RuntimeContext detect() {
        return detect(System::getProperty, System::getenv);
    }

    /**
     * Package-private testing seam. Both lookup functions accept a key and
     * return the value or {@code null}.
     */
    static RuntimeContext detect(Function<String, String> sysPropLookup, Function<String, String> envLookup) {
        String prop = sysPropLookup.apply(SYSTEM_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            return parseOverride(prop, "system property -D" + SYSTEM_PROPERTY);
        }
        String env = envLookup.apply(ENV_VAR);
        if (env != null && !env.isBlank()) {
            return parseOverride(env, "environment variable " + ENV_VAR);
        }
        String k8sHost = envLookup.apply(KUBERNETES_SERVICE_HOST_ENV);
        if (k8sHost != null && !k8sHost.isBlank()) {
            return IN_CLUSTER;
        }
        return LOCAL;
    }

    private static RuntimeContext parseOverride(String raw, String source) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "local":
                return LOCAL;
            case "in_cluster":
                return IN_CLUSTER;
            default:
                throw new IllegalStateException(
                    source + " has value '" + raw + "', which is not one of the allowed values "
                        + "'local' or 'in_cluster' (case-insensitive). "
                        + "Either correct the override, or unset it to fall back to autodetection.");
        }
    }
}
