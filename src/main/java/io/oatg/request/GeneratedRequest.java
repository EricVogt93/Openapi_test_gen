package io.oatg.request;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * A fully materialized HTTP request, ready to be executed or exported.
 *
 * @param operationId      operation this request exercises
 * @param method           upper-case HTTP method
 * @param pathTemplate     original path with {placeholders}
 * @param resolvedPath     path with generated, URL-encoded values substituted
 * @param queryParams      query parameters (a key may repeat for array values)
 * @param headers          header parameters incl. auth and extra static headers
 * @param cookies          cookie parameters
 * @param body             JSON body, or {@code null} if the operation has none
 * @param bodyMediaType    content type of the body, or {@code null}
 * @param expectedStatuses statuses declared in the spec (may contain "default"/"2XX")
 * @param seed             per-operation child seed used to generate this request
 */
public record GeneratedRequest(String operationId,
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
