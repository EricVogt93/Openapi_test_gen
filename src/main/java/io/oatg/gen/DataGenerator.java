package io.oatg.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.models.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema-driven test data generator — the AI-free core of the tool.
 *
 * <p>Value precedence: spec {@code example}/{@code examples} → {@code default}
 * → {@code enum} (seeded pick) → {@code const} → composition
 * ({@code allOf}/{@code oneOf}/{@code anyOf}) → {@code format} handler →
 * {@code pattern} → type-based constrained random.
 *
 * <p>Values generated for request bodies omit {@code readOnly} properties.
 */
public final class DataGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final int UNIQUE_ITEM_RETRIES = 10;
    private static final int DEFAULT_MAX_ARRAY_ITEMS = 3;

    private final StringGenerator strings = new StringGenerator();
    private final NumberGenerator numbers = new NumberGenerator();
    private final CompositionResolver compositions = new CompositionResolver();

    public JsonNode generate(Schema<?> schema, GeneratorContext ctx) {
        if (schema == null) {
            return NullNode.getInstance();
        }

        JsonNode fixed = fixedValue(schema, ctx);
        if (fixed != null) {
            return fixed;
        }

        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            return generate(compositions.mergeAllOf(schema, ctx), ctx);
        }
        List<Schema> branches = branchList(schema);
        if (branches != null) {
            Schema<?> pick = branches.get(ctx.random().nextInt(branches.size()));
            return generate(pick, ctx);
        }

        String type = resolveType(schema);
        return switch (type) {
            case "string" -> TextNode.valueOf(strings.generate(schema, ctx));
            case "integer" -> numbers.generate(schema, true, ctx);
            case "number" -> numbers.generate(schema, false, ctx);
            case "boolean" -> BooleanNode.valueOf(ctx.random().nextBoolean());
            case "array" -> generateArray(schema, ctx);
            case "object" -> generateObject(schema, ctx);
            default -> TextNode.valueOf(strings.generate(schema, ctx));
        };
    }

    /** example → default → enum → const, converted to a JsonNode. */
    private JsonNode fixedValue(Schema<?> schema, GeneratorContext ctx) {
        Object example = schema.getExample();
        if (example == null && schema.getExamples() != null && !schema.getExamples().isEmpty()) {
            example = schema.getExamples().get(0);
        }
        if (example != null) {
            return toNode(example, ctx);
        }
        if (schema.getDefault() != null) {
            return toNode(schema.getDefault(), ctx);
        }
        List<?> enums = schema.getEnum();
        if (enums != null && !enums.isEmpty()) {
            return toNode(enums.get(ctx.random().nextInt(enums.size())), ctx);
        }
        if (schema.getConst() != null) {
            return toNode(schema.getConst(), ctx);
        }
        return null;
    }

    private JsonNode toNode(Object value, GeneratorContext ctx) {
        try {
            return MAPPER.valueToTree(value);
        } catch (IllegalArgumentException e) {
            ctx.warn("Could not convert spec value '" + value + "' to JSON: " + e.getMessage());
            return TextNode.valueOf(String.valueOf(value));
        }
    }

    @SuppressWarnings("rawtypes")
    private List<Schema> branchList(Schema<?> schema) {
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            return schema.getOneOf();
        }
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return schema.getAnyOf();
        }
        return null;
    }

    private String resolveType(Schema<?> schema) {
        String type = schema.getType();
        if (type == null && schema.getTypes() != null && !schema.getTypes().isEmpty()) {
            // OpenAPI 3.1 type arrays: prefer the non-null variant (valid-path MVP)
            type = schema.getTypes().stream().filter(t -> !"null".equals(t)).findFirst().orElse("string");
        }
        if (type != null) {
            return type;
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return "object";
        }
        if (schema.getItems() != null) {
            return "array";
        }
        return "string";
    }

    private JsonNode generateObject(Schema<?> schema, GeneratorContext ctx) {
        ObjectNode node = NODES.objectNode();
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return node;
        }
        if (!ctx.enter(schema)) {
            ctx.warn("Recursion/depth limit reached at object schema; emitting empty object");
            return node;
        }
        try {
            Set<String> required = schema.getRequired() != null ? Set.copyOf(schema.getRequired()) : Set.of();
            for (Map.Entry<String, Schema> property : properties.entrySet()) {
                Schema<?> propSchema = property.getValue();
                if (Boolean.TRUE.equals(propSchema.getReadOnly())) {
                    continue; // request bodies must not carry readOnly properties
                }
                boolean include = required.contains(property.getKey()) || ctx.includeOptional();
                if (include) {
                    node.set(property.getKey(), generate(propSchema, ctx));
                }
            }
        } finally {
            ctx.exit();
        }
        return node;
    }

    private JsonNode generateArray(Schema<?> schema, GeneratorContext ctx) {
        ArrayNode node = NODES.arrayNode();
        Schema<?> items = schema.getItems();
        int lo = schema.getMinItems() != null ? Math.max(schema.getMinItems(), 0) : 1;
        int hi = schema.getMaxItems() != null
                ? schema.getMaxItems()
                : Math.max(lo, DEFAULT_MAX_ARRAY_ITEMS);
        hi = Math.min(hi, Math.max(lo, DEFAULT_MAX_ARRAY_ITEMS));
        if (hi < lo) {
            ctx.warn("maxItems " + schema.getMaxItems() + " < minItems " + schema.getMinItems() + "; using minItems");
            hi = lo;
        }
        int count = lo + (hi > lo ? ctx.random().nextInt(hi - lo + 1) : 0);
        if (count == 0) {
            return node;
        }
        if (!ctx.enter(schema)) {
            ctx.warn("Recursion/depth limit reached at array schema; emitting empty array");
            return node;
        }
        try {
            boolean unique = Boolean.TRUE.equals(schema.getUniqueItems());
            java.util.Set<String> seen = unique ? new java.util.HashSet<>() : null;
            for (int i = 0; i < count; i++) {
                JsonNode item = generate(items, ctx);
                if (unique) {
                    int attempts = 0;
                    while (!seen.add(item.toString()) && attempts++ < UNIQUE_ITEM_RETRIES) {
                        item = generate(items, ctx);
                    }
                    if (attempts > UNIQUE_ITEM_RETRIES) {
                        ctx.warn("Could not generate " + count + " unique array items; emitting " + node.size());
                        break;
                    }
                }
                node.add(item);
            }
        } finally {
            ctx.exit();
        }
        return node;
    }
}
