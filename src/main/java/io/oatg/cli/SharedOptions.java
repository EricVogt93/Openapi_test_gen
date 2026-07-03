package io.oatg.cli;

import io.oatg.OatgException;
import io.oatg.core.OatgConfig;
import io.oatg.gen.OptionalPropsMode;
import io.oatg.request.auth.AuthOptions;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Options shared by {@code generate} and {@code run}. */
public final class SharedOptions {

    @Option(names = "--spec", required = true, paramLabel = "<file|url>",
            description = "OpenAPI 3.0/3.1 document (file or URL), or a Swagger UI URL — "
                    + "the OpenAPI document is auto-discovered")
    String spec;

    @Option(names = "--spec-timeout", defaultValue = "10", paramLabel = "<seconds>",
            description = "Timeout per spec-discovery request (default: ${DEFAULT-VALUE}s)")
    long specTimeoutSeconds;

    @Option(names = "--out", paramLabel = "<dir>", defaultValue = "oatg-out",
            description = "Output directory (default: ${DEFAULT-VALUE})")
    Path out;

    @Option(names = "--seed", paramLabel = "<long>",
            description = "Root seed for reproducible data (default: random, printed on every run)")
    Long seed;

    @Option(names = "--include", paramLabel = "<glob>",
            description = "Only include paths matching this glob (repeatable), e.g. --include '/pets/**'")
    List<String> includes = new ArrayList<>();

    @Option(names = "--exclude", paramLabel = "<glob>",
            description = "Exclude paths matching this glob (repeatable)")
    List<String> excludes = new ArrayList<>();

    @Option(names = "--include-methods", split = ",", paramLabel = "<method>",
            description = "Only include these HTTP methods, e.g. GET,POST")
    Set<String> methods = Set.of();

    @Option(names = "--auth-bearer", paramLabel = "<token>", description = "Static bearer token")
    String authBearer;

    @Option(names = "--auth-basic", paramLabel = "<user:pass>", description = "Basic auth credentials")
    String authBasic;

    @Option(names = "--auth-apikey", paramLabel = "<NAME=VALUE[:header|query|cookie]>",
            description = "API key (repeatable); location defaults to the spec's securityScheme, else header")
    List<String> authApiKeys = new ArrayList<>();

    @Option(names = "--header", paramLabel = "<'K: V'>",
            description = "Extra static header for every request (repeatable)")
    List<String> headers = new ArrayList<>();

    @Option(names = "--optional-props", defaultValue = "always", paramLabel = "<always|never|random>",
            description = "Optional properties/parameters handling (default: ${DEFAULT-VALUE})")
    String optionalProps;

    @Option(names = "--max-depth", defaultValue = "5", paramLabel = "<n>",
            description = "Recursion cap for nested schemas (default: ${DEFAULT-VALUE})")
    int maxDepth;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    long resolveSeed() {
        return seed != null ? seed : new SecureRandom().nextLong();
    }

    static void printDiscovery(io.oatg.core.GenerationService.Outcome outcome, String rawSpec) {
        if (!outcome.specLocation().equals(rawSpec)) {
            System.out.println("Discovered OpenAPI document at " + outcome.specLocation());
        }
    }

    OatgConfig toConfig(long resolvedSeed) {
        try {
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new OatgException("Could not create output directory " + out + ": " + e.getMessage(), e);
        }
        return new OatgConfig(spec, out, resolvedSeed, includes, excludes,
                methods.stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(Collectors.toSet()),
                parseOptionalProps(), maxDepth, parseAuth(), parseHeaders(),
                Duration.ofSeconds(specTimeoutSeconds));
    }

    private OptionalPropsMode parseOptionalProps() {
        try {
            return OptionalPropsMode.valueOf(optionalProps.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new OatgException("Invalid --optional-props '" + optionalProps + "'; use always|never|random");
        }
    }

    private AuthOptions parseAuth() {
        return new AuthOptions(authBearer, authBasic,
                authApiKeys.stream().map(AuthOptions.ApiKey::parse).toList());
    }

    private Map<String, String> parseHeaders() {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String header : headers) {
            int colon = header.indexOf(':');
            if (colon <= 0) {
                throw new OatgException("Invalid --header '" + header + "'; expected 'Name: value'");
            }
            parsed.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
        }
        return parsed;
    }
}
