package io.oatg.request.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthOptionsTest {

    @Test
    void bearerTokenBecomesAuthorizationHeader() {
        AuthOptions options = new AuthOptions("tok-123", null, List.of());
        assertThat(options.asHeaders()).containsExactlyEntriesOf(
                Map.of("Authorization", "Bearer tok-123"));
    }

    @Test
    void basicCredentialsAreEncodedWhenNoBearerGiven() {
        AuthOptions options = new AuthOptions(null, "user:pass", List.of());
        assertThat(options.asHeaders())
                .containsEntry("Authorization", "Basic dXNlcjpwYXNz");
    }

    @Test
    void bearerWinsOverBasic() {
        AuthOptions options = new AuthOptions("tok", "user:pass", List.of());
        assertThat(options.asHeaders().get("Authorization")).isEqualTo("Bearer tok");
    }

    @Test
    void headerApiKeysIncludedQueryAndCookieKeysExcluded() {
        AuthOptions options = new AuthOptions("tok", null, List.of(
                new AuthOptions.ApiKey("X-Api-Key", "h1", AuthOptions.Location.HEADER),
                new AuthOptions.ApiKey("X-Default", "h2", null),
                new AuthOptions.ApiKey("api_key", "q1", AuthOptions.Location.QUERY),
                new AuthOptions.ApiKey("session", "c1", AuthOptions.Location.COOKIE)));
        Map<String, String> headers = options.asHeaders();
        assertThat(headers)
                .containsEntry("X-Api-Key", "h1")
                .containsEntry("X-Default", "h2")
                .doesNotContainKeys("api_key", "session");
    }

    @Test
    void noneYieldsNoHeaders() {
        assertThat(AuthOptions.none().asHeaders()).isEmpty();
    }
}
