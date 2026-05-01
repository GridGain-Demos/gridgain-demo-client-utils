package com.gridgain.demo.client;

import java.util.List;

/**
 * Thrown when the cluster has no addresses defined for the runtime context the
 * client is running in. The most common cause is a {@code LOCAL} client trying
 * to reach a cluster whose deploy was performed with {@code demo_access}
 * disabled (so the {@code local} addresses block was never written).
 */
public class ContextAddressesUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContextAddressesUnavailableException(
        String clusterName,
        RuntimeContext requestedContext,
        List<RuntimeContext> availableContexts
    ) {
        super(buildMessage(clusterName, requestedContext, availableContexts));
    }

    private static String buildMessage(
        String clusterName,
        RuntimeContext requestedContext,
        List<RuntimeContext> availableContexts
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cluster '").append(clusterName).append("' has no addresses available for runtime context ")
            .append(requestedContext).append(". ");
        if (availableContexts.isEmpty()) {
            sb.append("In fact, no contexts are populated for this cluster at all, which suggests the ");
            sb.append("client-endpoints.yaml entry is malformed. Re-run the plugin deploy task.");
        } else {
            sb.append("Contexts that ARE available for this cluster: ").append(availableContexts).append(". ");
            if (requestedContext == RuntimeContext.LOCAL) {
                sb.append("If you are running the client on your laptop, re-deploy the cluster with ");
                sb.append("demo_access enabled so the plugin emits 'local' addresses. ");
                sb.append("Or set -Dgg.demo.client.context=in_cluster (or env var GG_DEMO_CLIENT_CONTEXT=in_cluster) ");
                sb.append("if you are actually running inside the Kubernetes cluster.");
            } else {
                sb.append("If you are running inside Kubernetes, verify the cluster was deployed to k8s. ");
                sb.append("Or set -Dgg.demo.client.context=local (or env var GG_DEMO_CLIENT_CONTEXT=local) ");
                sb.append("if you are actually running on a laptop.");
            }
        }
        return sb.toString();
    }
}
