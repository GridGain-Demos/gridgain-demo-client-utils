package com.gridgain.demo.client;

import java.util.List;

/**
 * Thrown when a caller asks for a cluster name that is not present in the
 * loaded {@code client-endpoints.yaml}.
 */
public class NoSuchClusterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoSuchClusterException(String requestedName, List<String> availableNames) {
        super(buildMessage(requestedName, availableNames));
    }

    private static String buildMessage(String requestedName, List<String> availableNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("No cluster named '").append(requestedName).append("' was found in client-endpoints.yaml. ");
        if (availableNames.isEmpty()) {
            sb.append("The file contained no clusters at all. ");
            sb.append("Verify that the gridgain-demo-gradle-plugin deployment task ran to completion ");
            sb.append("and produced a non-empty client-endpoints.yaml.");
        } else {
            sb.append("Available clusters: ").append(availableNames).append(". ");
            sb.append("Either pass one of those names, or re-run the plugin's deploy task with the ");
            sb.append("cluster you expected.");
        }
        return sb.toString();
    }
}
