package io.oatg.request.auth;

import io.oatg.OatgException;

import java.util.List;
import java.util.Locale;

/**
 * Static credentials supplied on the CLI. OAuth flows are out of scope; a
 * manually obtained bearer token covers OAuth-protected APIs in practice.
 */
public record AuthOptions(String bearerToken, String basicCredentials, List<ApiKey> apiKeys) {

    public static AuthOptions none() {
        return new AuthOptions(null, null, List.of());
    }

    /**
     * @param name     header/query/cookie parameter name
     * @param value    the key value
     * @param location where to send it, or {@code null} to let the spec's
     *                 securityScheme decide (falls back to header)
     */
    public record ApiKey(String name, String value, Location location) {

        /** Parses {@code NAME=VALUE} or {@code NAME=VALUE:header|query|cookie}. */
        public static ApiKey parse(String raw) {
            int eq = raw.indexOf('=');
            if (eq <= 0) {
                throw new OatgException("Invalid --auth-apikey value '" + raw + "'; expected NAME=VALUE[:header|query|cookie]");
            }
            String name = raw.substring(0, eq);
            String rest = raw.substring(eq + 1);
            Location location = null;
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                String suffix = rest.substring(colon + 1).toLowerCase(Locale.ROOT);
                switch (suffix) {
                    case "header", "query", "cookie" -> {
                        location = Location.valueOf(suffix.toUpperCase(Locale.ROOT));
                        rest = rest.substring(0, colon);
                    }
                    default -> { /* the colon belongs to the value */ }
                }
            }
            return new ApiKey(name, rest, location);
        }
    }

    public enum Location { HEADER, QUERY, COOKIE }
}
