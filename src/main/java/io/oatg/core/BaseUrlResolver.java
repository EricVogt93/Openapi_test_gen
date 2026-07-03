package io.oatg.core;

import io.swagger.v3.oas.models.OpenAPI;

import java.net.URI;

/**
 * Single place for the effective-base-URL precedence used by both commands:
 * {@code --base-url} flag → absolute spec server → relative spec server
 * resolved against the discovered spec origin → discovered origin itself.
 */
public final class BaseUrlResolver {

    private BaseUrlResolver() {
    }

    /** @param source human-readable provenance for console output */
    public record BaseUrl(String url, String source) {
    }

    /** @return the effective base URL, or {@code null} when nothing applies (local file, no servers). */
    public static BaseUrl resolve(String flag, OpenAPI api, String discoveredOrigin) {
        if (flag != null) {
            return new BaseUrl(flag, "--base-url");
        }
        String server = firstServerUrl(api);
        if (server != null && server.startsWith("http")) {
            return new BaseUrl(server, "first spec server");
        }
        if (discoveredOrigin != null) {
            if (server != null && server.startsWith("/")) {
                return new BaseUrl(URI.create(discoveredOrigin + "/").resolve(server).toString(),
                        "spec server '" + server + "' resolved against spec origin");
            }
            // no usable server (missing, or templated like {scheme}://…) — fall back to the origin
            return new BaseUrl(discoveredOrigin, "origin of discovered spec URL");
        }
        return null;
    }

    private static String firstServerUrl(OpenAPI api) {
        if (api.getServers() == null || api.getServers().isEmpty()) {
            return null;
        }
        return api.getServers().get(0).getUrl();
    }
}
