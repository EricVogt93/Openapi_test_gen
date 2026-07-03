package io.oatg.request;

import io.oatg.core.GenerationService;
import io.oatg.core.OatgConfig;
import io.oatg.gen.OptionalPropsMode;
import io.oatg.request.auth.AuthOptions;
import io.oatg.spi.Extensions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAssemblerTest {

    @TempDir
    static Path tempDir;

    private static GenerationService.Outcome outcome;

    @BeforeAll
    static void prepare() {
        OatgConfig config = new OatgConfig(
                "src/test/resources/specs/petstore.yaml", tempDir, 42L,
                List.of(), List.of(), Set.of(), OptionalPropsMode.ALWAYS, 5,
                new AuthOptions("test-token", null,
                        List.of(new AuthOptions.ApiKey("api_key", "secret", null))),
                Map.of("X-Custom", "1"), Duration.ofSeconds(5));
        outcome = new GenerationService(Extensions.defaults()).prepare(config);
    }

    private static GeneratedRequest byId(String operationId) {
        return outcome.requests().stream()
                .filter(r -> r.operationId().equals(operationId)).findFirst().orElseThrow();
    }

    @Test
    void pathParametersAreSubstitutedAndValid() {
        GeneratedRequest getPet = byId("getPet");
        assertThat(getPet.resolvedPath()).matches("/pets/\\d+");
        assertThat(getPet.resolvedPath()).doesNotContain("{");
    }

    @Test
    void requiredQueryParametersArePresentAndValid() {
        GeneratedRequest listPets = byId("listPets");
        List<String> limit = listPets.queryParams().get("limit");
        assertThat(limit).hasSize(1);
        assertThat(Integer.parseInt(limit.get(0))).isBetween(1, 100);
    }

    @Test
    void arrayQueryParameterRepeatsKey() {
        GeneratedRequest listPets = byId("listPets");
        List<String> tags = listPets.queryParams().get("tags");
        assertThat(tags).isNotEmpty();
        tags.forEach(t -> assertThat(t.length()).isBetween(2, 10));
    }

    @Test
    void bearerTokenAndApiKeyAreApplied() {
        GeneratedRequest addPet = byId("addPet");
        assertThat(addPet.headers()).containsEntry("Authorization", "Bearer test-token");
        // api_key scheme is declared as a query parameter in the spec
        assertThat(addPet.queryParams()).containsEntry("api_key", List.of("secret"));
        assertThat(addPet.headers()).containsEntry("X-Custom", "1");
    }

    @Test
    void bodyHonorsConstraintsAndOmitsReadOnly() {
        GeneratedRequest addPet = byId("addPet");
        assertThat(addPet.body()).isNotNull();
        assertThat(addPet.body().get("name").asText().length()).isBetween(1, 30);
        assertThat(addPet.body().get("code").asText()).matches("^[A-Z]{3}-[0-9]{4}$");
        assertThat(addPet.body().get("contactEmail").asText()).contains("@");
        assertThat(addPet.body().has("id")).isFalse(); // Pet.id is readOnly anyway not in NewPet
    }

    @Test
    void oneOfBodyMatchesExactlyOneBranch() {
        GeneratedRequest updatePet = byId("updatePet");
        String petType = updatePet.body().get("petType").asText();
        assertThat(petType).isIn("cat", "dog");
        if (petType.equals("cat")) {
            assertThat(updatePet.body().has("meows")).isTrue();
        } else {
            assertThat(updatePet.body().get("barkVolume").asInt()).isBetween(0, 11);
        }
    }

    @Test
    void multipleOfConstraintHolds() {
        GeneratedRequest placeOrder = byId("placeOrder");
        int quantity = placeOrder.body().get("quantity").asInt();
        assertThat(quantity).isBetween(1, 10);
        assertThat(quantity % 2).isZero();
    }

    @Test
    void sameSeedIsFullyDeterministic() {
        OatgConfig config = new OatgConfig(
                "src/test/resources/specs/petstore.yaml", tempDir, 42L,
                List.of(), List.of(), Set.of(), OptionalPropsMode.ALWAYS, 5,
                new AuthOptions("test-token", null,
                        List.of(new AuthOptions.ApiKey("api_key", "secret", null))),
                Map.of("X-Custom", "1"), Duration.ofSeconds(5));
        GenerationService.Outcome second = new GenerationService(Extensions.defaults()).prepare(config);
        assertThat(second.requests()).isEqualTo(outcome.requests());
    }
}
