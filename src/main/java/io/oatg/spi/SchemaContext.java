package io.oatg.spi;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Describes where a generated value will be used.
 *
 * @param operationId the OpenAPI operation the value belongs to
 * @param location    {@code "body"} or the parameter name (e.g. {@code "query:limit"})
 * @param schema      the schema the value must satisfy
 */
public record SchemaContext(String operationId, String location, Schema<?> schema) {
}
