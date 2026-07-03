package io.oatg.spi.noop;

import com.fasterxml.jackson.databind.JsonNode;
import io.oatg.spi.SchemaContext;
import io.oatg.spi.TestDataEnhancer;

/** Default enhancer: keeps the deterministically generated value. */
public final class NoOpTestDataEnhancer implements TestDataEnhancer {

    @Override
    public JsonNode enhance(SchemaContext context, JsonNode candidate) {
        return candidate;
    }
}
