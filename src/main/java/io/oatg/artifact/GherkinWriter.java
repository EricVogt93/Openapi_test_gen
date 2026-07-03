package io.oatg.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.oatg.OatgException;
import io.oatg.request.GeneratedRequest;
import io.oatg.spi.TestManagementPublisher.GeneratedScenario;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emits one {@code .feature} file per group (tag or path). Scenarios are
 * tagged with {@code @generated @seed-<n> @operation-<id>} so a later Xray
 * import can key on them.
 */
public final class GherkinWriter {

    public enum GroupMode { TAG, PATH }

    private final ObjectMapper mapper = new ObjectMapper();

    public record Result(List<Path> files, List<GeneratedScenario> scenarios) {
    }

    /**
     * @param tagsByOperationId first spec tag per operation, used in {@link GroupMode#TAG}
     * @param rootSeed          the run's root seed — reproduces the whole run via {@code --seed}
     */
    public Result write(OpenAPI api, List<GeneratedRequest> requests,
                        Map<String, List<String>> tagsByOperationId, GroupMode mode,
                        long rootSeed, Path outDir) {
        Path featureDir = outDir.resolve("features");
        try {
            Files.createDirectories(featureDir);
        } catch (IOException e) {
            throw new OatgException("Could not create " + featureDir + ": " + e.getMessage(), e);
        }

        Map<String, List<GeneratedRequest>> groups = new LinkedHashMap<>();
        for (GeneratedRequest request : requests) {
            String key = mode == GroupMode.PATH
                    ? firstPathSegment(request.pathTemplate())
                    : firstTag(tagsByOperationId.getOrDefault(request.operationId(), List.of()));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(request);
        }

        String apiTitle = api.getInfo() != null && api.getInfo().getTitle() != null
                ? api.getInfo().getTitle() : "API";

        List<Path> files = new ArrayList<>();
        List<GeneratedScenario> scenarios = new ArrayList<>();
        for (Map.Entry<String, List<GeneratedRequest>> group : groups.entrySet()) {
            String featureName = apiTitle + " — " + group.getKey();
            StringBuilder sb = new StringBuilder();
            sb.append("Feature: ").append(featureName).append('\n');
            for (GeneratedRequest request : group.getValue()) {
                String scenario = scenario(request, rootSeed);
                sb.append('\n').append(scenario);
                scenarios.add(new GeneratedScenario(request.operationId(), featureName, scenario,
                        List.of("@generated", "@seed-" + rootSeed,
                                "@operation-" + sanitize(request.operationId()))));
            }
            Path file = featureDir.resolve(sanitize(group.getKey()) + ".feature");
            try {
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new OatgException("Could not write " + file + ": " + e.getMessage(), e);
            }
            files.add(file);
        }
        return new Result(files, scenarios);
    }

    private String scenario(GeneratedRequest r, long rootSeed) {
        StringBuilder sb = new StringBuilder();
        sb.append("  @generated @seed-").append(rootSeed)
          .append(" @operation-").append(sanitize(r.operationId())).append('\n');
        sb.append("  Scenario: ").append(r.method()).append(' ').append(r.pathTemplate())
          .append(" returns a documented response").append('\n');
        sb.append("    Given the API at base URL \"{baseUrl}\"\n");
        sb.append("    When I send a ").append(r.method())
          .append(" request to \"").append(r.resolvedPath()).append('"');
        if (!r.queryParams().isEmpty()) {
            sb.append(" with query parameters:\n");
            for (Map.Entry<String, List<String>> q : r.queryParams().entrySet()) {
                for (String value : q.getValue()) {
                    sb.append("      | ").append(q.getKey()).append(" | ").append(value).append(" |\n");
                }
            }
        } else {
            sb.append('\n');
        }
        if (r.body() != null) {
            sb.append("    And the request body is:\n");
            sb.append("      \"\"\"\n");
            String pretty;
            try {
                pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(r.body());
            } catch (IOException e) {
                pretty = r.body().toString();
            }
            pretty.lines().forEach(line -> sb.append("      ").append(line).append('\n'));
            sb.append("      \"\"\"\n");
        }
        sb.append("    Then the response status should be one of ")
          .append(r.expectedStatuses().isEmpty() ? "2XX" : String.join(", ", r.expectedStatuses()))
          .append('\n');
        return sb.toString();
    }

    private static String firstTag(List<String> tags) {
        return tags.isEmpty() ? "default" : tags.get(0);
    }

    private static String firstPathSegment(String pathTemplate) {
        for (String segment : pathTemplate.split("/")) {
            if (!segment.isBlank() && !segment.startsWith("{")) {
                return segment;
            }
        }
        return "root";
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }
}
