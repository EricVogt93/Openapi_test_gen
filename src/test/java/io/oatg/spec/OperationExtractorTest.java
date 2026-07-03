package io.oatg.spec;

import io.oatg.spec.model.EndpointOperation;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationExtractorTest {

    private static OperationExtractor.ExtractionResult result;

    @BeforeAll
    static void extract() {
        OpenAPI api = new SpecLoader().load("src/test/resources/specs/petstore.yaml");
        result = new OperationExtractor().extract(api);
    }

    @Test
    void extractsAllSupportedOperations() {
        List<String> ids = result.operations().stream().map(EndpointOperation::operationId).toList();
        assertThat(ids).containsExactlyInAnyOrder(
                "listPets", "addPet", "getPet", "updatePet", "deletePet", "placeOrder");
    }

    @Test
    void skipsMultipartOperationWithReason() {
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).pathTemplate()).isEqualTo("/pets/{petId}/photo");
        assertThat(result.skipped().get(0).reason()).contains("multipart/form-data");
    }

    @Test
    void mergesPathLevelParameters() {
        EndpointOperation getPet = result.operations().stream()
                .filter(o -> o.operationId().equals("getPet")).findFirst().orElseThrow();
        assertThat(getPet.parameters()).extracting(p -> p.getName()).containsExactly("petId");
    }

    @Test
    void capturesDeclaredStatusesIncludingDefault() {
        EndpointOperation placeOrder = result.operations().stream()
                .filter(o -> o.operationId().equals("placeOrder")).findFirst().orElseThrow();
        assertThat(placeOrder.declaredStatuses()).containsExactlyInAnyOrder("201", "default");
    }

    @Test
    void capturesBodySchemaForJsonOperations() {
        EndpointOperation addPet = result.operations().stream()
                .filter(o -> o.operationId().equals("addPet")).findFirst().orElseThrow();
        assertThat(addPet.bodySchema()).isNotNull();
        assertThat(addPet.bodyMediaType()).isEqualTo("application/json");
    }
}
