package io.oatg.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extension point for enhancing generated test data, e.g. with an AI backend
 * (local Qwen via an OpenAI-compatible endpoint) that produces more realistic
 * values. Implementations MUST return a value that is still valid against the
 * schema described by the context; the core does not re-validate.
 *
 * <p>The default implementation is a no-op that returns the candidate unchanged.
 */
public interface TestDataEnhancer {

    /**
     * @param context   where the value will be used (operation, location, schema)
     * @param candidate the schema-valid value produced by the deterministic generator
     * @return the value to use; return {@code candidate} to keep the generated one
     */
    JsonNode enhance(SchemaContext context, JsonNode candidate);
}
