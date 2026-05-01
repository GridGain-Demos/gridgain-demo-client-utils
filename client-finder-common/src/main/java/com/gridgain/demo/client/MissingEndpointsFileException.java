package com.gridgain.demo.client;

import java.nio.file.Path;
import java.util.List;

/**
 * Thrown when {@link EndpointsFileLocator#locate()} cannot find a
 * {@code client-endpoints.yaml} via any supported resolution step.
 *
 * <p>The message lists each location that was checked along with remediation
 * pointers, so the user can pick the override mechanism that fits their
 * deployment shape.</p>
 */
public class MissingEndpointsFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MissingEndpointsFileException(
        String systemPropertyValue,
        String envVarValue,
        String classpathResource,
        Path cwdDefault,
        List<String> attemptedLocations
    ) {
        super(buildMessage(systemPropertyValue, envVarValue, classpathResource, cwdDefault, attemptedLocations));
    }

    private static String buildMessage(
        String systemPropertyValue,
        String envVarValue,
        String classpathResource,
        Path cwdDefault,
        List<String> attemptedLocations
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Could not locate client-endpoints.yaml. The following locations were checked, in order:\n");
        for (int i = 0; i < attemptedLocations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(attemptedLocations.get(i)).append('\n');
        }
        sb.append("\nTo fix this, do one of the following:\n");
        sb.append("  - Set system property -Dgg.demo.client.endpoints=/absolute/path/to/client-endpoints.yaml\n");
        sb.append("  - Set environment variable GG_DEMO_CLIENT_ENDPOINTS=/absolute/path/to/client-endpoints.yaml\n");
        sb.append("  - Package the file as classpath resource '").append(classpathResource).append("'\n");
        sb.append("  - Run from a directory whose 'build/demo-output/client/client-endpoints.yaml' exists\n");
        sb.append("    (last attempted: ").append(cwdDefault).append(")");
        if (systemPropertyValue != null) {
            sb.append("\nNote: system property gg.demo.client.endpoints was set to '")
                .append(systemPropertyValue).append("' but that file was not readable.");
        }
        if (envVarValue != null) {
            sb.append("\nNote: env var GG_DEMO_CLIENT_ENDPOINTS was set to '")
                .append(envVarValue).append("' but that file was not readable.");
        }
        return sb.toString();
    }
}
