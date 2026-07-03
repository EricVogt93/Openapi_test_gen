package io.oatg.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oatg.OatgException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the raw {@code --spec} value. Accepts a local file path, a direct
 * OpenAPI document URL, or a Swagger UI page URL (swagger-ui.html,
 * /swagger-ui/index.html, or just the service origin) and auto-discovers the
 * actual OpenAPI document behind it.
 *
 * <p>Discovery order for an HTML page: deep-link query params ({@code ?url=},
 * {@code ?configUrl=}) → inline page config → sibling
 * {@code swagger-initializer.js} (Swagger UI 4/5) → springdoc
 * {@code /v3/api-docs/swagger-config} → well-known path probing.
 * Content is always sniffed — Content-Type headers are not trusted.
 */
public final class SpecResolver {

    private static final Logger log = LoggerFactory.getLogger(SpecResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PROBES = 25;

    private static final Pattern URL_PROP = Pattern.compile("\\burl\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern CONFIG_URL_PROP = Pattern.compile("\\bconfigUrl\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern YAML_ROOT_KEY = Pattern.compile("^(openapi|swagger)\\s*:\\s*\\S+");

    /** The stock swagger-ui-dist default config points here when unconfigured. */
    private static final String UNCONFIGURED_DEFAULT_HOST = "petstore.swagger.io";

    private static final List<String> WELL_KNOWN_PATHS = List.of(
            "v3/api-docs", "v3/api-docs.yaml", "openapi.json", "openapi.yaml", "openapi",
            "swagger.json", "api-docs", "swagger/v1/swagger.json", "q/openapi");

    private final HttpClient client;
    private final Duration timeout;

    /**
     * @param specLocation     file path or URL to hand to {@link SpecLoader}
     * @param suggestedBaseUrl origin ({@code scheme://host[:port]}) of the
     *                         discovered spec URL; {@code null} for local files
     * @param discovered       true when the location differs from the raw input
     */
    public record Resolved(String specLocation, String suggestedBaseUrl, boolean discovered) {
    }

    public SpecResolver(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Resolved resolve(String rawSpec) {
        if (!rawSpec.startsWith("http://") && !rawSpec.startsWith("https://")) {
            return new Resolved(rawSpec, null, false); // local file — SpecLoader validates existence
        }

        List<String> tried = new ArrayList<>();
        Fetched page = fetch(URI.create(rawSpec), tried).orElseThrow(() ->
                new OatgException("Could not reach --spec URL " + rawSpec));

        if (looksLikeOpenApiDocument(page.body())) {
            String location = page.finalUri().toString();
            return new Resolved(location, origin(page.finalUri()), !location.equals(rawSpec));
        }

        LinkedHashSet<URI> candidates = new LinkedHashSet<>();
        if (looksLikeHtml(page.contentType(), page.body())) {
            queryParamCandidates(page.finalUri()).forEach(c -> addCandidate(candidates, page.finalUri(), c));
            extractUrlCandidates(page.body()).forEach(c -> addCandidate(candidates, page.finalUri(), c));

            fetch(page.finalUri().resolve("swagger-initializer.js"), tried).ifPresent(js ->
                    extractUrlCandidates(js.body()).forEach(c -> addCandidate(candidates, page.finalUri(), c)));

            if (page.finalUri().getPath() != null && page.finalUri().getPath().contains("swagger-ui")) {
                addCandidate(candidates, page.finalUri(), "/v3/api-docs/swagger-config");
            }
        }
        wellKnownCandidates(page.finalUri()).forEach(candidates::add);

        Resolved resolved = probe(deprioritizeForeignDefaults(candidates, page.finalUri()), tried);
        if (resolved != null) {
            return resolved;
        }
        throw new OatgException("--spec " + rawSpec + " is neither an OpenAPI document nor a Swagger UI page "
                + "the spec could be auto-discovered from. Tried:\n  " + String.join("\n  ", tried)
                + "\nPass the OpenAPI document URL directly (e.g. http://host:port/v3/api-docs).");
    }

    private Resolved probe(List<URI> candidates, List<String> tried) {
        List<URI> queue = new ArrayList<>(candidates);
        LinkedHashSet<URI> seen = new LinkedHashSet<>(queue);
        int probes = 0;
        while (!queue.isEmpty() && probes < MAX_PROBES) {
            URI candidate = queue.remove(0);
            probes++;
            Optional<Fetched> doc = fetch(candidate, tried);
            if (doc.isEmpty()) {
                continue;
            }
            if (looksLikeOpenApiDocument(doc.get().body())) {
                URI found = doc.get().finalUri();
                log.debug("Discovered OpenAPI document at {}", found);
                return new Resolved(found.toString(), origin(found), true);
            }
            // swagger-config style JSON: has url/urls but is not itself a spec
            List<String> fromConfig = candidatesFromSwaggerConfig(doc.get().body());
            int insertAt = 0;
            for (String c : fromConfig) {
                URI next = doc.get().finalUri().resolve(c);
                if (seen.add(next)) {
                    queue.add(insertAt++, next); // config-provided URLs are authoritative — probe first
                }
            }
        }
        return null;
    }

    // --- fetching -----------------------------------------------------------

    private record Fetched(URI finalUri, String contentType, String body) {
    }

    private Optional<Fetched> fetch(URI uri, List<String> tried) {
        tried.add(uri.toString());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "application/json, application/yaml, text/html, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                tried.set(tried.size() - 1, uri + " (HTTP " + response.statusCode() + ")");
                return Optional.empty();
            }
            return Optional.of(new Fetched(response.uri(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OatgException("Interrupted while fetching " + uri);
        } catch (IOException | IllegalArgumentException e) {
            tried.set(tried.size() - 1, uri + " (" + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
            return Optional.empty();
        }
    }

    // --- content sniffing (package-private for unit tests) -------------------

    static boolean looksLikeOpenApiDocument(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String trimmed = stripBom(body).stripLeading();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = MAPPER.readTree(trimmed);
                return root.has("openapi") || root.has("swagger");
            } catch (IOException e) {
                return false;
            }
        }
        return trimmed.lines()
                .limit(50)
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .anyMatch(l -> YAML_ROOT_KEY.matcher(l).find());
    }

    static boolean looksLikeHtml(String contentType, String body) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String head = body.substring(0, Math.min(body.length(), 512)).toLowerCase(Locale.ROOT);
        return head.contains("<!doctype") || head.contains("<html");
    }

    // --- candidate extraction (package-private for unit tests) ---------------

    /** configUrl candidates first (they point at swagger-config), then url/urls entries. */
    static List<String> extractUrlCandidates(String jsOrHtml) {
        List<String> candidates = new ArrayList<>();
        Matcher config = CONFIG_URL_PROP.matcher(jsOrHtml);
        while (config.find()) {
            candidates.add(config.group(1));
        }
        Matcher url = URL_PROP.matcher(jsOrHtml);
        while (url.find()) {
            candidates.add(url.group(1));
        }
        return candidates;
    }

    static List<String> candidatesFromSwaggerConfig(String json) {
        List<String> candidates = new ArrayList<>();
        String trimmed = stripBom(json).stripLeading();
        if (!trimmed.startsWith("{")) {
            return candidates;
        }
        try {
            JsonNode root = MAPPER.readTree(trimmed);
            if (root.hasNonNull("url")) {
                candidates.add(root.get("url").asText());
            }
            JsonNode urls = root.get("urls");
            if (urls != null && urls.isArray()) {
                urls.forEach(entry -> {
                    if (entry.hasNonNull("url")) {
                        candidates.add(entry.get("url").asText());
                    }
                });
            }
            JsonNode configUrl = root.get("configUrl");
            if (configUrl != null && candidates.isEmpty()) {
                candidates.add(configUrl.asText());
            }
        } catch (IOException e) {
            // not JSON — nothing to extract
        }
        return candidates;
    }

    static List<String> queryParamCandidates(URI pageUri) {
        List<String> candidates = new ArrayList<>();
        String query = pageUri.getRawQuery();
        if (query == null) {
            return candidates;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = param.substring(0, eq);
            if (name.equals("url") || name.equals("configUrl")) {
                candidates.add(URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return candidates;
    }

    static String origin(URI uri) {
        StringBuilder sb = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            sb.append(':').append(uri.getPort());
        }
        return sb.toString();
    }

    /**
     * Base path of the Swagger UI, with trailing {@code index.html},
     * {@code swagger-ui} or {@code swagger-ui.html} segments stripped — this is
     * where a context-path deployment keeps its {@code /v3/api-docs}.
     */
    static String uiParentPath(URI pageUri) {
        String path = pageUri.getPath() != null ? pageUri.getPath() : "/";
        path = path.replaceAll("/[^/]*\\.html$", "/")
                .replaceAll("/index\\.html$", "/")
                .replaceAll("/swagger-ui/?$", "/");
        if (!path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf('/') + 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    List<URI> wellKnownCandidates(URI pageUri) {
        LinkedHashSet<URI> candidates = new LinkedHashSet<>();
        String base = origin(pageUri);
        String parent = uiParentPath(pageUri);
        for (String path : WELL_KNOWN_PATHS) {
            if (!parent.equals("/")) {
                candidates.add(URI.create(base + parent + path));
            }
            candidates.add(URI.create(base + "/" + path));
        }
        return new ArrayList<>(candidates);
    }

    private static void addCandidate(LinkedHashSet<URI> candidates, URI pageUri, String candidate) {
        try {
            candidates.add(pageUri.resolve(candidate.trim()));
        } catch (IllegalArgumentException e) {
            // malformed candidate in page source — skip
        }
    }

    /** The stock swagger-ui-dist demo URL is almost never the user's API — probe it last. */
    private static List<URI> deprioritizeForeignDefaults(LinkedHashSet<URI> candidates, URI pageUri) {
        List<URI> preferred = new ArrayList<>();
        List<URI> deferred = new ArrayList<>();
        for (URI candidate : candidates) {
            boolean foreignDefault = candidate.getHost() != null
                    && candidate.getHost().equalsIgnoreCase(UNCONFIGURED_DEFAULT_HOST)
                    && !UNCONFIGURED_DEFAULT_HOST.equalsIgnoreCase(pageUri.getHost());
            (foreignDefault ? deferred : preferred).add(candidate);
        }
        preferred.addAll(deferred);
        return preferred;
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
