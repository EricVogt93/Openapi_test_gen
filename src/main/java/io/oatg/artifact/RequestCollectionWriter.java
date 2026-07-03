package io.oatg.artifact;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oatg.OatgException;
import io.oatg.request.GeneratedRequest;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code requests.json} — a versioned, self-describing collection of
 * all materialized requests. The format is documented in the README and can be
 * transformed into other collection formats (Postman, Bruno, …) downstream.
 */
public final class RequestCollectionWriter {

    public static final int COLLECTION_VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public record Collection(int collectionVersion,
                             String generatedBy,
                             SpecInfo spec,
                             long seed,
                             String baseUrl,
                             List<Entry> requests) {
    }

    public record SpecInfo(String title, String version) {
    }

    public record Entry(String operationId,
                        String method,
                        String pathTemplate,
                        String resolvedPath,
                        Map<String, List<String>> queryParams,
                        Map<String, String> headers,
                        Map<String, String> cookies,
                        JsonNode body,
                        String bodyMediaType,
                        List<String> expectedStatuses,
                        long seed) {
    }

    public Path write(OpenAPI api, List<GeneratedRequest> requests, long seed, String baseUrl, Path outDir) {
        SpecInfo info = new SpecInfo(
                api.getInfo() != null ? api.getInfo().getTitle() : null,
                api.getInfo() != null ? api.getInfo().getVersion() : null);
        List<Entry> entries = requests.stream()
                .map(r -> new Entry(r.operationId(), r.method(), r.pathTemplate(), r.resolvedPath(),
                        r.queryParams(), r.headers(), r.cookies(), r.body(), r.bodyMediaType(),
                        r.expectedStatuses(), r.seed()))
                .toList();
        Collection collection = new Collection(COLLECTION_VERSION, "oatg", info, seed, baseUrl, entries);

        Path file = outDir.resolve("requests.json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), collection);
        } catch (IOException e) {
            throw new OatgException("Could not write " + file + ": " + e.getMessage(), e);
        }
        return file;
    }
}
