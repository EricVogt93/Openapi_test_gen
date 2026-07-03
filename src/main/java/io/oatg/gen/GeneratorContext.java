package io.oatg.gen;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Per-operation generation state: seeded randomness, config and a schema stack
 * that guards against cyclic schemas and unbounded recursion.
 */
public final class GeneratorContext {

    private final Random random;
    private final long seed;
    private final OptionalPropsMode optionalProps;
    private final int maxDepth;
    private final FormatRegistry formats;
    private final List<String> warnings = new ArrayList<>();
    private final Deque<Schema<?>> stack = new ArrayDeque<>();

    public GeneratorContext(long seed, OptionalPropsMode optionalProps, int maxDepth) {
        this.seed = seed;
        this.random = new Random(seed);
        this.optionalProps = optionalProps;
        this.maxDepth = maxDepth;
        this.formats = new FormatRegistry(random);
    }

    public Random random() {
        return random;
    }

    public long seed() {
        return seed;
    }

    public FormatRegistry formats() {
        return formats;
    }

    public boolean includeOptional() {
        return switch (optionalProps) {
            case ALWAYS -> true;
            case NEVER -> false;
            case RANDOM -> random.nextBoolean();
        };
    }

    /**
     * Enters a container schema (object/array). Returns {@code false} when the
     * depth limit is reached or the same schema instance is already on the
     * stack (cycle from a recursive $ref) — the caller must emit a fallback.
     */
    public boolean enter(Schema<?> schema) {
        if (stack.size() >= maxDepth || onStack(schema)) {
            return false;
        }
        stack.push(schema);
        return true;
    }

    public void exit() {
        stack.pop();
    }

    private boolean onStack(Schema<?> schema) {
        for (Schema<?> s : stack) {
            if (s == schema) {
                return true;
            }
        }
        return false;
    }

    public void warn(String message) {
        warnings.add(message);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
