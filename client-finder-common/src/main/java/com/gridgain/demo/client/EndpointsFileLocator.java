package com.gridgain.demo.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Resolves the location of {@code client-endpoints.yaml} on disk (or
 * extracts it from the classpath into a temp file if that is where it was
 * packaged).
 *
 * <p>Resolution order, first hit wins:</p>
 * <ol>
 *   <li>System property {@value #SYSTEM_PROPERTY} (absolute path).</li>
 *   <li>Environment variable {@value #ENV_VAR} (absolute path).</li>
 *   <li>Classpath resource {@value #CLASSPATH_RESOURCE}; if present, copied
 *       to a temp file (delete-on-exit) so the loader can treat it as a
 *       {@link Path}.</li>
 *   <li>{@code <user.dir>/build/demo-output/client/client-endpoints.yaml}.</li>
 * </ol>
 *
 * <p>If none of the above resolves to a readable file, throws
 * {@link MissingEndpointsFileException} with a message that lists every
 * location checked plus remediation pointers.</p>
 */
public final class EndpointsFileLocator {

    public static final String SYSTEM_PROPERTY = "gg.demo.client.endpoints";
    public static final String ENV_VAR = "GG_DEMO_CLIENT_ENDPOINTS";
    public static final String CLASSPATH_RESOURCE = "/gridgain-demo/client-endpoints.yaml";
    static final String CWD_RELATIVE_PATH = "build/demo-output/client/client-endpoints.yaml";

    private EndpointsFileLocator() {
        // utility class
    }

    /** Production API. */
    public static Path locate() {
        return locate(System::getProperty, System::getenv, EndpointsFileLocator.class.getClassLoader());
    }

    /**
     * Package-private testing seam. The classloader is parameterised so tests
     * can construct one whose classpath does or does not contain the fixture
     * resource without polluting the JVM-wide classpath.
     */
    static Path locate(
        Function<String, String> sysPropLookup,
        Function<String, String> envLookup,
        ClassLoader classpathLookup
    ) {
        List<String> attempted = new ArrayList<>(4);

        String sysPropValue = sysPropLookup.apply(SYSTEM_PROPERTY);
        attempted.add(describeOverrideAttempt("System property -D" + SYSTEM_PROPERTY, sysPropValue));
        if (sysPropValue != null && !sysPropValue.isBlank()) {
            Path candidate = Paths.get(sysPropValue);
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }

        String envValue = envLookup.apply(ENV_VAR);
        attempted.add(describeOverrideAttempt("Environment variable " + ENV_VAR, envValue));
        if (envValue != null && !envValue.isBlank()) {
            Path candidate = Paths.get(envValue);
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }

        attempted.add("Classpath resource '" + CLASSPATH_RESOURCE + "'");
        Path classpathExtracted = tryClasspathResource(classpathLookup);
        if (classpathExtracted != null) {
            return classpathExtracted;
        }

        Path cwdDefault = Paths.get(System.getProperty("user.dir"), "build", "demo-output", "client", "client-endpoints.yaml");
        attempted.add("Working-directory default: " + cwdDefault);
        if (Files.isReadable(cwdDefault)) {
            return cwdDefault;
        }

        throw new MissingEndpointsFileException(
            (sysPropValue != null && !sysPropValue.isBlank()) ? sysPropValue : null,
            (envValue != null && !envValue.isBlank()) ? envValue : null,
            CLASSPATH_RESOURCE,
            cwdDefault,
            attempted
        );
    }

    private static String describeOverrideAttempt(String source, String value) {
        if (value == null) {
            return source + " (not set)";
        }
        if (value.isBlank()) {
            return source + " (set but blank)";
        }
        return source + " = " + value;
    }

    private static Path tryClasspathResource(ClassLoader cl) {
        // Class.getResourceAsStream uses class-relative paths if no leading slash;
        // we want absolute (root of classpath), and ClassLoader.getResourceAsStream
        // expects no leading slash. Strip it.
        String resourcePath = CLASSPATH_RESOURCE.startsWith("/")
            ? CLASSPATH_RESOURCE.substring(1)
            : CLASSPATH_RESOURCE;
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Path tmp = Files.createTempFile("gridgain-demo-client-endpoints-", ".yaml");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (IOException e) {
            // Treat IO errors here as "not located via classpath" but with a
            // wrapped cause so the user sees something. We deliberately do not
            // silently fall through if the resource was found but couldn't be
            // copied — that is a real failure.
            throw new RuntimeException(
                "Found classpath resource '" + CLASSPATH_RESOURCE
                    + "' but failed to copy it to a temporary file. "
                    + "Check that the JVM has write access to its temp directory.", e);
        }
    }
}
