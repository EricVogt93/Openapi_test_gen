package io.oatg.spec;

import io.oatg.OatgException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live smoke test against the public Swagger petstore. Opt-in only
 * ({@code OATG_LIVE_TESTS=true}) and tolerant of proxy/network blocks —
 * a network failure aborts instead of failing the build.
 */
@EnabledIfEnvironmentVariable(named = "OATG_LIVE_TESTS", matches = "true")
class PetstoreLiveSmokeIT {

    @Test
    void discoversSpecBehindSwaggerUi() {
        SpecResolver resolver = new SpecResolver(Duration.ofSeconds(15));
        SpecResolver.Resolved resolved;
        try {
            resolved = resolver.resolve("https://petstore3.swagger.io/");
        } catch (OatgException e) {
            throw new TestAbortedException("petstore3.swagger.io unreachable (proxy/network): " + e.getMessage());
        }
        assertThat(resolved.discovered()).isTrue();
        assertThat(resolved.specLocation()).contains("openapi.json");
        assertThat(resolved.suggestedBaseUrl()).isEqualTo("https://petstore3.swagger.io");
    }
}
