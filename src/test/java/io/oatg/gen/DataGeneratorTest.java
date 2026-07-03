package io.oatg.gen;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven tests of the generator against tricky schemas; output is
 * validated programmatically against the schema constraints.
 */
class DataGeneratorTest {

    private static OpenAPI api;
    private final DataGenerator generator = new DataGenerator();

    @BeforeAll
    static void loadSpec() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        api = new OpenAPIV3Parser()
                .readLocation("src/test/resources/specs/tricky-schemas.yaml", null, options)
                .getOpenAPI();
        assertThat(api).isNotNull();
    }

    private static Schema<?> schema(String name) {
        Schema<?> s = api.getComponents().getSchemas().get(name);
        assertThat(s).as("schema " + name).isNotNull();
        return s;
    }

    private GeneratorContext ctx(long seed) {
        return new GeneratorContext(seed, OptionalPropsMode.ALWAYS, 5);
    }

    @Test
    void patternWithLengthConstraints() {
        for (long seed = 0; seed < 20; seed++) {
            JsonNode node = generator.generate(schema("PatternWithLength"), ctx(seed));
            assertThat(node.isTextual()).isTrue();
            assertThat(node.asText()).matches("^[A-Z]{3}-[0-9]{4}$");
            assertThat(node.asText()).hasSize(8);
        }
    }

    @Test
    void multipleOfWithinBounds() {
        for (long seed = 0; seed < 50; seed++) {
            long value = generator.generate(schema("BoundedMultiple"), ctx(seed)).asLong();
            assertThat(value).isBetween(7L, 100L);
            assertThat(value % 9).isZero();
        }
    }

    @Test
    void exclusiveBoundsLeaveOnlyOneValue() {
        for (long seed = 0; seed < 20; seed++) {
            long value = generator.generate(schema("ExclusiveBounds"), ctx(seed)).asLong();
            assertThat(value).isEqualTo(6L);
        }
    }

    @Test
    void negativeDecimalRange() {
        for (long seed = 0; seed < 50; seed++) {
            double value = generator.generate(schema("NegativeRange"), ctx(seed)).asDouble();
            assertThat(value).isBetween(-50.5, -10.25);
        }
    }

    @Test
    void allOfMergesPropertiesAndRequired() {
        JsonNode node = generator.generate(schema("AllOfMerge"), ctx(1));
        assertThat(node.isObject()).isTrue();
        assertThat(node.get("alpha").asText().length()).isGreaterThanOrEqualTo(5);
        assertThat(node.get("beta").asLong()).isBetween(100L, 200L);
    }

    @Test
    void oneOfPicksAValidBranch() {
        for (long seed = 0; seed < 20; seed++) {
            JsonNode node = generator.generate(schema("OneOfChoice"), ctx(seed));
            if (node.isTextual()) {
                assertThat(node.asText()).isEqualTo("left");
            } else {
                assertThat(node.asLong()).isBetween(1000L, 1001L);
            }
        }
    }

    @Test
    void recursiveSchemaTerminates() {
        JsonNode node = generator.generate(schema("RecursiveNode"), ctx(7));
        assertThat(node.isObject()).isTrue();
        assertThat(node.get("value").isTextual()).isTrue();
        // must not blow the stack; depth is capped
        assertThat(node.toString().length()).isLessThan(100_000);
    }

    @Test
    void nullableStringStaysNonNullOnValidPath() {
        JsonNode node = generator.generate(schema("NullableString"), ctx(3));
        assertThat(node.isTextual()).isTrue();
        assertThat(node.asText().length()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void uniqueArrayItemsAreUnique() {
        for (long seed = 0; seed < 20; seed++) {
            JsonNode node = generator.generate(schema("UniqueEnumArray"), ctx(seed));
            assertThat(node.isArray()).isTrue();
            assertThat(node.size()).isBetween(2, 3);
            long distinct = java.util.stream.StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asLong).distinct().count();
            assertThat(distinct).isEqualTo(node.size());
        }
    }

    @Test
    void formatsProduceValidValues() {
        JsonNode node = generator.generate(schema("FormatShowcase"), ctx(11));
        assertThat(UUID.fromString(node.get("id").asText())).isNotNull();
        assertThat(node.get("email").asText()).contains("@");
        assertThat(node.get("createdAt").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})");
        assertThat(node.get("birthday").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(node.get("homepage").asText()).startsWith("https://");
        assertThat(node.get("host").asText()).matches("[a-z0-9.-]+");
        assertThat(node.get("ip").asText())
                .matches(Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}"));
        assertThat(Base64.getDecoder().decode(node.get("blob").asText())).isNotEmpty();
    }

    @Test
    void specExampleWinsOverGeneration() {
        assertThat(generator.generate(schema("WithExample"), ctx(5)).asText()).isEqualTo("from-the-spec");
    }

    @Test
    void defaultValueWinsOverGeneration() {
        assertThat(generator.generate(schema("WithDefault"), ctx(5)).asInt()).isEqualTo(42);
    }

    @Test
    void emptyRangeFallsBackWithWarning() {
        GeneratorContext ctx = ctx(5);
        JsonNode node = generator.generate(schema("EmptyRange"), ctx);
        assertThat(node.isNumber()).isTrue();
        assertThat(ctx.warnings()).isNotEmpty();
    }

    @Test
    void sameSeedProducesIdenticalOutput() {
        for (String name : api.getComponents().getSchemas().keySet()) {
            JsonNode first = generator.generate(schema(name), ctx(99));
            JsonNode second = generator.generate(schema(name), ctx(99));
            assertThat(second).as("schema " + name).isEqualTo(first);
        }
    }

    @Test
    void differentSeedsProduceDifferentStrings() {
        JsonNode a = generator.generate(schema("PatternWithLength"), ctx(1));
        JsonNode b = generator.generate(schema("PatternWithLength"), ctx(2));
        // not guaranteed for every schema, but for an 8-char pattern collision is unlikely
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void optionalPropsNeverModeOmitsOptionals() {
        GeneratorContext ctx = new GeneratorContext(1, OptionalPropsMode.NEVER, 5);
        JsonNode node = generator.generate(schema("RecursiveNode"), ctx);
        assertThat(node.has("value")).isTrue();
        assertThat(node.has("children")).isFalse();
    }
}
