package io.oatg.gen;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Generates strings honoring {@code format}, {@code pattern}, {@code minLength}
 * and {@code maxLength}. Precedence: format → pattern → constrained random.
 */
final class StringGenerator {

    private static final int DEFAULT_MAX_LENGTH = 64;
    private static final int DEFAULT_LENGTH = 12;
    private static final int PATTERN_RETRIES = 25;

    String generate(Schema<?> schema, GeneratorContext ctx) {
        String format = schema.getFormat();
        if (format != null) {
            var formatted = ctx.formats().generate(format);
            if (formatted.isPresent()) {
                return formatted.get();
            }
        }
        if (schema.getPattern() != null) {
            return fromPattern(schema, ctx);
        }
        return constrainedRandom(schema, ctx);
    }

    private String fromPattern(Schema<?> schema, GeneratorContext ctx) {
        String pattern = schema.getPattern();
        RgxGen rgxGen;
        try {
            rgxGen = RgxGen.parse(pattern);
        } catch (RuntimeException e) {
            ctx.warn("Unsupported pattern '" + pattern + "' (" + e.getMessage() + "); using plain string");
            return constrainedRandom(schema, ctx);
        }
        Integer min = schema.getMinLength();
        Integer max = schema.getMaxLength();
        String candidate = rgxGen.generate(ctx.random());
        if (min == null && max == null) {
            return candidate;
        }
        for (int i = 0; i < PATTERN_RETRIES && !lengthOk(candidate, min, max); i++) {
            candidate = rgxGen.generate(ctx.random());
        }
        if (!lengthOk(candidate, min, max)) {
            ctx.warn("Could not satisfy pattern '" + pattern + "' together with minLength/maxLength"
                    + " after " + PATTERN_RETRIES + " attempts; pattern wins");
        }
        return candidate;
    }

    private static boolean lengthOk(String s, Integer min, Integer max) {
        return (min == null || s.length() >= min) && (max == null || s.length() <= max);
    }

    private String constrainedRandom(Schema<?> schema, GeneratorContext ctx) {
        Integer minLength = schema.getMinLength();
        Integer maxLength = schema.getMaxLength();
        int lo = minLength != null ? Math.max(minLength, 0) : 1;
        int hi = maxLength != null ? Math.min(maxLength, DEFAULT_MAX_LENGTH) : Math.max(lo, DEFAULT_LENGTH);
        if (hi < lo) {
            ctx.warn("maxLength " + maxLength + " < minLength " + minLength + "; using minLength");
            hi = lo;
        }
        int length = lo + (hi > lo ? ctx.random().nextInt(hi - lo + 1) : 0);
        return ctx.formats().alphanumeric(length);
    }
}
