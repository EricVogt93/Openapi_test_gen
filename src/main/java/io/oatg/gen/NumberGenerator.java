package io.oatg.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Generates integers and numbers honoring {@code minimum}, {@code maximum},
 * exclusive bounds (both the 3.0 boolean and the 3.1 value form) and
 * {@code multipleOf}.
 */
final class NumberGenerator {

    private static final BigDecimal DEFAULT_SPAN = BigDecimal.valueOf(1000);
    private static final long MAX_RANDOM_SPAN = 1_000_000L;

    JsonNode generate(Schema<?> schema, boolean integer, GeneratorContext ctx) {
        Bounds bounds = resolveBounds(schema, integer, ctx);
        BigDecimal multipleOf = schema.getMultipleOf();

        BigDecimal value;
        if (multipleOf != null && multipleOf.signum() > 0) {
            value = multipleInRange(bounds, multipleOf, ctx);
        } else if (integer) {
            value = randomInteger(bounds, ctx);
        } else {
            value = randomDecimal(bounds, ctx);
        }

        if (integer) {
            long l = value.setScale(0, RoundingMode.HALF_UP).longValueExact();
            if ("int32".equals(schema.getFormat())) {
                return IntNode.valueOf((int) l);
            }
            return LongNode.valueOf(l);
        }
        if ("float".equals(schema.getFormat()) || "double".equals(schema.getFormat()) || value.scale() > 0) {
            return DoubleNode.valueOf(value.doubleValue());
        }
        return DecimalNode.valueOf(value);
    }

    private record Bounds(BigDecimal lo, boolean loExclusive, BigDecimal hi, boolean hiExclusive) {
    }

    private Bounds resolveBounds(Schema<?> schema, boolean integer, GeneratorContext ctx) {
        BigDecimal min = schema.getMinimum();
        BigDecimal max = schema.getMaximum();
        boolean loExclusive = Boolean.TRUE.equals(schema.getExclusiveMinimum());
        boolean hiExclusive = Boolean.TRUE.equals(schema.getExclusiveMaximum());
        if (schema.getExclusiveMinimumValue() != null) { // OpenAPI 3.1 form
            min = schema.getExclusiveMinimumValue();
            loExclusive = true;
        }
        if (schema.getExclusiveMaximumValue() != null) {
            max = schema.getExclusiveMaximumValue();
            hiExclusive = true;
        }

        if (min == null && max == null) {
            min = BigDecimal.ZERO;
            max = DEFAULT_SPAN;
        } else if (min == null) {
            min = max.subtract(DEFAULT_SPAN);
        } else if (max == null) {
            max = min.add(DEFAULT_SPAN);
        }
        if (integer && "int32".equals(schema.getFormat())) {
            min = min.max(BigDecimal.valueOf(Integer.MIN_VALUE));
            max = max.min(BigDecimal.valueOf(Integer.MAX_VALUE));
        }
        if (max.compareTo(min) < 0) {
            ctx.warn("maximum " + max + " < minimum " + min + "; using minimum");
            max = min;
            loExclusive = false;
            hiExclusive = false;
        }
        return new Bounds(min, loExclusive, max, hiExclusive);
    }

    private BigDecimal multipleInRange(Bounds b, BigDecimal m, GeneratorContext ctx) {
        BigDecimal kLoDec = b.lo().divide(m, 0, RoundingMode.CEILING);
        BigDecimal kHiDec = b.hi().divide(m, 0, RoundingMode.FLOOR);
        long kLo = kLoDec.longValueExact();
        long kHi = kHiDec.longValueExact();
        if (b.loExclusive() && m.multiply(BigDecimal.valueOf(kLo)).compareTo(b.lo()) == 0) {
            kLo++;
        }
        if (b.hiExclusive() && m.multiply(BigDecimal.valueOf(kHi)).compareTo(b.hi()) == 0) {
            kHi--;
        }
        if (kLo > kHi) {
            ctx.warn("No multiple of " + m + " within [" + b.lo() + ", " + b.hi() + "]; using minimum");
            return b.lo();
        }
        long k = kLo + boundedRandom(kHi - kLo, ctx);
        return m.multiply(BigDecimal.valueOf(k)).stripTrailingZeros();
    }

    private BigDecimal randomInteger(Bounds b, GeneratorContext ctx) {
        long lo = b.lo().setScale(0, RoundingMode.CEILING).longValueExact();
        long hi = b.hi().setScale(0, RoundingMode.FLOOR).longValueExact();
        if (b.loExclusive() && BigDecimal.valueOf(lo).compareTo(b.lo()) == 0) {
            lo++;
        }
        if (b.hiExclusive() && BigDecimal.valueOf(hi).compareTo(b.hi()) == 0) {
            hi--;
        }
        if (lo > hi) {
            ctx.warn("Empty integer range [" + b.lo() + ", " + b.hi() + "]; using lower bound");
            return b.lo().setScale(0, RoundingMode.CEILING);
        }
        return BigDecimal.valueOf(lo + boundedRandom(hi - lo, ctx));
    }

    private BigDecimal randomDecimal(Bounds b, GeneratorContext ctx) {
        double lo = b.lo().doubleValue();
        double hi = b.hi().doubleValue();
        double span = hi - lo;
        if (span <= 0) {
            return b.lo();
        }
        // keep away from exclusive edges by shrinking the sampled window slightly
        double margin = (b.loExclusive() || b.hiExclusive()) ? span * 0.001 : 0;
        double value = lo + margin + ctx.random().nextDouble() * (span - 2 * margin);
        return BigDecimal.valueOf(value);
    }

    /** Uniform value in [0, span], robust against overflow for huge spans. */
    private long boundedRandom(long span, GeneratorContext ctx) {
        if (span <= 0) {
            return 0;
        }
        long effective = Math.min(span, MAX_RANDOM_SPAN);
        return ctx.random().nextLong(effective + 1);
    }
}
