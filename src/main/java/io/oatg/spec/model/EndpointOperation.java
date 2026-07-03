package io.oatg.spec.model;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;

import java.util.List;

/**
 * A single HTTP operation extracted from the spec, with path-level and
 * operation-level parameters already merged.
 *
 * @param operationId      operation id from the spec, or a synthesized fallback
 * @param method           upper-case HTTP method
 * @param pathTemplate     path with {placeholders}
 * @param parameters       merged parameters (path + operation level)
 * @param bodySchema       JSON request body schema, or {@code null} if no body is sent
 * @param bodyMediaType    media type of the body, or {@code null}
 * @param security         effective security requirements (operation level, falling back to global)
 * @param declaredStatuses response status codes declared in the spec (may contain "default" or "2XX")
 * @param tags             operation tags (possibly empty)
 * @param summary          operation summary, or {@code null}
 */
public record EndpointOperation(String operationId,
                                String method,
                                String pathTemplate,
                                List<Parameter> parameters,
                                Schema<?> bodySchema,
                                String bodyMediaType,
                                List<SecurityRequirement> security,
                                List<String> declaredStatuses,
                                List<String> tags,
                                String summary) {

    /** Stable identifier used for per-operation seeding and reporting. */
    public String key() {
        return method + " " + pathTemplate;
    }
}
