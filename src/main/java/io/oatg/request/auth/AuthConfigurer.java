package io.oatg.request.auth;

import io.oatg.spec.model.EndpointOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Applies static credentials to a request. Credentials are applied whenever
 * supplied — matching the spec's {@code securitySchemes} where possible to
 * decide the location of API keys.
 */
public final class AuthConfigurer {

    private final OpenAPI api;
    private final AuthOptions options;

    public AuthConfigurer(OpenAPI api, AuthOptions options) {
        this.api = api;
        this.options = options;
    }

    public void apply(EndpointOperation op,
                      Map<String, String> headers,
                      Map<String, List<String>> queryParams,
                      Map<String, String> cookies) {
        if (options.bearerToken() != null) {
            headers.put("Authorization", "Bearer " + options.bearerToken());
        } else if (options.basicCredentials() != null) {
            String encoded = Base64.getEncoder()
                    .encodeToString(options.basicCredentials().getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        }
        for (AuthOptions.ApiKey key : options.apiKeys()) {
            AuthOptions.Location location = key.location() != null ? key.location() : locationFromSpec(key.name());
            switch (location) {
                case QUERY -> queryParams.put(key.name(), List.of(key.value()));
                case COOKIE -> cookies.put(key.name(), key.value());
                case HEADER -> headers.put(key.name(), key.value());
            }
        }
    }

    private AuthOptions.Location locationFromSpec(String keyName) {
        if (api.getComponents() != null && api.getComponents().getSecuritySchemes() != null) {
            for (SecurityScheme scheme : api.getComponents().getSecuritySchemes().values()) {
                if (scheme.getType() == SecurityScheme.Type.APIKEY && keyName.equals(scheme.getName())
                        && scheme.getIn() != null) {
                    return switch (scheme.getIn()) {
                        case QUERY -> AuthOptions.Location.QUERY;
                        case COOKIE -> AuthOptions.Location.COOKIE;
                        case HEADER -> AuthOptions.Location.HEADER;
                    };
                }
            }
        }
        return AuthOptions.Location.HEADER;
    }
}
