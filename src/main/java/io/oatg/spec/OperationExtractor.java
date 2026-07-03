package io.oatg.spec;

import io.oatg.spec.model.EndpointOperation;
import io.oatg.spec.model.SkippedOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flattens the spec's paths/operations into {@link EndpointOperation}s.
 * Operations whose required request body has no JSON media type are reported
 * as skipped instead of being dropped silently.
 */
public final class OperationExtractor {

    public record ExtractionResult(List<EndpointOperation> operations, List<SkippedOperation> skipped) {
    }

    public ExtractionResult extract(OpenAPI api) {
        List<EndpointOperation> operations = new ArrayList<>();
        List<SkippedOperation> skipped = new ArrayList<>();

        for (Map.Entry<String, PathItem> pathEntry : api.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();

            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : item.readOperationsMap().entrySet()) {
                String method = opEntry.getKey().name();
                Operation op = opEntry.getValue();

                List<Parameter> params = mergeParameters(item.getParameters(), op.getParameters());

                Schema<?> bodySchema = null;
                String bodyMediaType = null;
                RequestBody body = op.getRequestBody();
                if (body != null && body.getContent() != null && !body.getContent().isEmpty()) {
                    Map.Entry<String, MediaType> json = pickJsonMediaType(body.getContent());
                    if (json != null) {
                        bodyMediaType = json.getKey();
                        bodySchema = json.getValue().getSchema();
                    } else if (Boolean.TRUE.equals(body.getRequired())) {
                        skipped.add(new SkippedOperation(method, path,
                                "required request body has no JSON media type (found: "
                                        + String.join(", ", body.getContent().keySet()) + ")"));
                        continue;
                    }
                    // optional non-JSON body: proceed without a body
                }

                List<SecurityRequirement> security = op.getSecurity() != null
                        ? op.getSecurity()
                        : (api.getSecurity() != null ? api.getSecurity() : List.of());

                List<String> declaredStatuses = op.getResponses() != null
                        ? List.copyOf(op.getResponses().keySet())
                        : List.of();

                operations.add(new EndpointOperation(
                        operationId(op, method, path),
                        method,
                        path,
                        params,
                        bodySchema,
                        bodyMediaType,
                        security,
                        declaredStatuses,
                        op.getTags() != null ? op.getTags() : List.of(),
                        op.getSummary()));
            }
        }
        return new ExtractionResult(operations, skipped);
    }

    private static List<Parameter> mergeParameters(List<Parameter> pathLevel, List<Parameter> opLevel) {
        // operation-level parameters override path-level ones with the same (name, in)
        Map<String, Parameter> merged = new LinkedHashMap<>();
        if (pathLevel != null) {
            pathLevel.forEach(p -> merged.put(paramKey(p), p));
        }
        if (opLevel != null) {
            opLevel.forEach(p -> merged.put(paramKey(p), p));
        }
        return List.copyOf(merged.values());
    }

    private static String paramKey(Parameter p) {
        return p.getIn() + ":" + p.getName();
    }

    private static Map.Entry<String, MediaType> pickJsonMediaType(Content content) {
        for (Map.Entry<String, MediaType> e : content.entrySet()) {
            if (e.getKey().equalsIgnoreCase("application/json")) {
                return e;
            }
        }
        for (Map.Entry<String, MediaType> e : content.entrySet()) {
            String mt = e.getKey().toLowerCase(Locale.ROOT);
            if (mt.contains("json")) { // application/problem+json, application/vnd.api+json, …
                return e;
            }
        }
        return null;
    }

    private static String operationId(Operation op, String method, String path) {
        if (op.getOperationId() != null && !op.getOperationId().isBlank()) {
            return op.getOperationId();
        }
        return (method + path).replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
    }
}
