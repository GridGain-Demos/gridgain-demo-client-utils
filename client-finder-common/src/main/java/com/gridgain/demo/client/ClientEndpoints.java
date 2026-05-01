package com.gridgain.demo.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory representation of {@code client-endpoints.yaml}.
 *
 * <p>This is a hand-written POJO hierarchy rather than a record graph because:
 * (a) we need to control snake_case to camelCase mapping explicitly without
 * reflection or Jackson, (b) records and inheritance/inner-record mapping
 * helpers compose awkwardly, and (c) plain getters keep the surface obvious to
 * downstream finder libraries.</p>
 *
 * <p>The {@code fromMap} factory methods accept the raw {@link Map} produced by
 * SnakeYAML's safe constructor and convert it to the strongly-typed shape.
 * Unknown keys are tolerated (so the plugin can add new optional fields
 * without breaking older client-utils versions); missing required keys throw
 * with a clear remediation message.</p>
 */
public final class ClientEndpoints {

    private final int schemaVersion;
    private final List<Cluster> clusters;

    public ClientEndpoints(int schemaVersion, List<Cluster> clusters) {
        this.schemaVersion = schemaVersion;
        this.clusters = List.copyOf(Objects.requireNonNull(clusters, "clusters"));
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    /**
     * Look up a cluster by name. Throws if no matching cluster exists; the
     * thrown exception lists all available cluster names.
     */
    public Cluster findCluster(String name) {
        Objects.requireNonNull(name, "name");
        for (Cluster c : clusters) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        List<String> available = new ArrayList<>(clusters.size());
        for (Cluster c : clusters) {
            available.add(c.getName());
        }
        throw new NoSuchClusterException(name, available);
    }

    @SuppressWarnings("unchecked")
    static ClientEndpoints fromMap(Map<String, Object> root) {
        Objects.requireNonNull(root, "root map");
        Object versionObj = requireKey(root, "schema_version", "<root>");
        int version = toInt(versionObj, "schema_version", "<root>");
        Object clustersObj = requireKey(root, "clusters", "<root>");
        if (!(clustersObj instanceof List<?>)) {
            throw new IllegalArgumentException(
                "Field 'clusters' in client-endpoints.yaml must be a YAML list, but was "
                    + describeType(clustersObj)
                    + ". Re-run the plugin deploy task to regenerate the file.");
        }
        List<Cluster> clusters = new ArrayList<>();
        int idx = 0;
        for (Object item : (List<?>) clustersObj) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(
                    "Entry " + idx + " under 'clusters' in client-endpoints.yaml must be a YAML map, but was "
                        + describeType(item) + ".");
            }
            clusters.add(Cluster.fromMap((Map<String, Object>) item, "clusters[" + idx + "]"));
            idx++;
        }
        return new ClientEndpoints(version, clusters);
    }

    /** A single cluster entry from the file. */
    public static final class Cluster {
        private final String name;
        private final String namespace;
        private final int gridgainMajorVersion;
        private final Contexts contexts;

        public Cluster(String name, String namespace, int gridgainMajorVersion, Contexts contexts) {
            this.name = Objects.requireNonNull(name, "name");
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            this.gridgainMajorVersion = gridgainMajorVersion;
            this.contexts = Objects.requireNonNull(contexts, "contexts");
        }

        public String getName() {
            return name;
        }

        public String getNamespace() {
            return namespace;
        }

        public int getGridgainMajorVersion() {
            return gridgainMajorVersion;
        }

        public Contexts getContexts() {
            return contexts;
        }

        @SuppressWarnings("unchecked")
        static Cluster fromMap(Map<String, Object> map, String path) {
            String name = requireString(requireKey(map, "name", path), "name", path);
            String namespace = requireString(requireKey(map, "namespace", path), "namespace", path);
            int major = toInt(requireKey(map, "gridgain_major_version", path), "gridgain_major_version", path);
            Object contextsObj = requireKey(map, "contexts", path);
            if (!(contextsObj instanceof Map)) {
                throw new IllegalArgumentException(
                    "Field 'contexts' at " + path + " must be a YAML map, but was "
                        + describeType(contextsObj) + ".");
            }
            Contexts contexts = Contexts.fromMap((Map<String, Object>) contextsObj, path + ".contexts");
            return new Cluster(name, namespace, major, contexts);
        }
    }

    /**
     * Container for the per-context address blocks. {@link #local} is
     * deliberately nullable: the plugin omits it entirely when the cluster
     * was deployed without demo_access enabled.
     */
    public static final class Contexts {
        private final Addresses local; // nullable: absent when demo_access disabled at deploy time
        private final Addresses inCluster;

        public Contexts(Addresses local, Addresses inCluster) {
            this.local = local;
            this.inCluster = Objects.requireNonNull(inCluster, "inCluster");
        }

        /** May return {@code null} if the cluster was deployed without demo_access. */
        public Addresses getLocal() {
            return local;
        }

        public Addresses getInCluster() {
            return inCluster;
        }

        @SuppressWarnings("unchecked")
        static Contexts fromMap(Map<String, Object> map, String path) {
            Addresses local = null;
            if (map.containsKey("local")) {
                Object localObj = map.get("local");
                if (localObj == null) {
                    // Explicit null is also tolerated: treat as absent.
                    local = null;
                } else if (!(localObj instanceof Map)) {
                    throw new IllegalArgumentException(
                        "Field 'local' at " + path + " must be a YAML map (or omitted), but was "
                            + describeType(localObj) + ".");
                } else {
                    local = Addresses.fromMap((Map<String, Object>) localObj, path + ".local");
                }
            }
            Object inClusterObj = requireKey(map, "in_cluster", path);
            if (!(inClusterObj instanceof Map)) {
                throw new IllegalArgumentException(
                    "Field 'in_cluster' at " + path + " must be a YAML map, but was "
                        + describeType(inClusterObj) + ".");
            }
            Addresses inCluster = Addresses.fromMap((Map<String, Object>) inClusterObj, path + ".in_cluster");
            return new Contexts(local, inCluster);
        }
    }

    /** A single address block. The plugin emits an {@code addresses:} list. */
    public static final class Addresses {
        private final List<String> addresses;

        public Addresses(List<String> addresses) {
            this.addresses = List.copyOf(Objects.requireNonNull(addresses, "addresses"));
        }

        public List<String> getAddresses() {
            return addresses;
        }

        @SuppressWarnings("unchecked")
        static Addresses fromMap(Map<String, Object> map, String path) {
            Object obj = requireKey(map, "addresses", path);
            if (!(obj instanceof List<?>)) {
                throw new IllegalArgumentException(
                    "Field 'addresses' at " + path + " must be a YAML list, but was "
                        + describeType(obj) + ".");
            }
            List<String> out = new ArrayList<>();
            int idx = 0;
            for (Object item : (List<?>) obj) {
                if (item == null) {
                    throw new IllegalArgumentException(
                        "Entry " + idx + " of 'addresses' at " + path + " is null. "
                            + "Each entry must be a 'host:port' string.");
                }
                if (!(item instanceof String)) {
                    throw new IllegalArgumentException(
                        "Entry " + idx + " of 'addresses' at " + path + " must be a string, but was "
                            + describeType(item) + ". Each entry must be a 'host:port' string.");
                }
                out.add((String) item);
                idx++;
            }
            return new Addresses(Collections.unmodifiableList(out));
        }
    }

    // ---- Internal helpers ---------------------------------------------------

    private static Object requireKey(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException(
                "client-endpoints.yaml is missing required field '" + key + "' at " + path
                    + ". Re-run the plugin deploy task to regenerate the file.");
        }
        Object val = map.get(key);
        if (val == null) {
            throw new IllegalArgumentException(
                "client-endpoints.yaml has a null value for required field '" + key + "' at " + path
                    + ". Re-run the plugin deploy task to regenerate the file.");
        }
        return val;
    }

    private static int toInt(Object obj, String fieldName, String path) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Field '" + fieldName + "' at " + path + " must be an integer, but was the string '"
                        + obj + "'.", e);
            }
        }
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' at " + path + " must be an integer, but was "
                + describeType(obj) + ".");
    }

    private static String requireString(Object obj, String fieldName, String path) {
        if (obj instanceof String) {
            return (String) obj;
        }
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' at " + path + " must be a string, but was "
                + describeType(obj) + ".");
    }

    private static String describeType(Object obj) {
        if (obj == null) return "null";
        return obj.getClass().getSimpleName();
    }
}
