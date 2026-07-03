package io.oatg.gen;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges {@code allOf} branches into a single effective schema. Properties and
 * required sets are unioned; scalar constraints are last-wins (a conflict is
 * reported as a warning).
 */
final class CompositionResolver {

    @SuppressWarnings({"unchecked", "rawtypes"})
    Schema<?> mergeAllOf(Schema<?> schema, GeneratorContext ctx) {
        Schema merged = new Schema<>();
        List<Schema> branches = new ArrayList<>();
        branches.add(schema); // the host schema may carry its own properties/constraints
        branches.addAll(schema.getAllOf());

        Map<String, Schema> properties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();

        for (Schema branch : branches) {
            if (branch == schema && branch.getAllOf() != null) {
                // avoid re-processing the allOf list itself on the host copy
            }
            if (branch.getProperties() != null) {
                properties.putAll(branch.getProperties());
            }
            if (branch.getRequired() != null) {
                required.addAll(branch.getRequired());
            }
            copyScalarConstraints(branch, merged, ctx);
        }
        if (!properties.isEmpty()) {
            merged.setProperties(properties);
        }
        if (!required.isEmpty()) {
            merged.setRequired(new ArrayList<>(required));
        }
        if (merged.getType() == null && (merged.getTypes() == null || merged.getTypes().isEmpty())
                && !properties.isEmpty()) {
            merged.setType("object");
        }
        return merged;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copyScalarConstraints(Schema from, Schema into, GeneratorContext ctx) {
        if (from.getType() != null) {
            warnOnConflict("type", into.getType(), from.getType(), ctx);
            into.setType(from.getType());
        }
        if (from.getTypes() != null && !from.getTypes().isEmpty()) {
            into.setTypes(from.getTypes());
        }
        if (from.getFormat() != null) {
            into.setFormat(from.getFormat());
        }
        if (from.getPattern() != null) {
            into.setPattern(from.getPattern());
        }
        if (from.getMinLength() != null) {
            into.setMinLength(from.getMinLength());
        }
        if (from.getMaxLength() != null) {
            into.setMaxLength(from.getMaxLength());
        }
        if (from.getMinimum() != null) {
            into.setMinimum(from.getMinimum());
        }
        if (from.getMaximum() != null) {
            into.setMaximum(from.getMaximum());
        }
        if (from.getExclusiveMinimum() != null) {
            into.setExclusiveMinimum(from.getExclusiveMinimum());
        }
        if (from.getExclusiveMaximum() != null) {
            into.setExclusiveMaximum(from.getExclusiveMaximum());
        }
        if (from.getExclusiveMinimumValue() != null) {
            into.setExclusiveMinimumValue(from.getExclusiveMinimumValue());
        }
        if (from.getExclusiveMaximumValue() != null) {
            into.setExclusiveMaximumValue(from.getExclusiveMaximumValue());
        }
        if (from.getMultipleOf() != null) {
            into.setMultipleOf(from.getMultipleOf());
        }
        if (from.getEnum() != null && !from.getEnum().isEmpty()) {
            into.setEnum(from.getEnum());
        }
        if (from.getItems() != null) {
            into.setItems(from.getItems());
        }
        if (from.getMinItems() != null) {
            into.setMinItems(from.getMinItems());
        }
        if (from.getMaxItems() != null) {
            into.setMaxItems(from.getMaxItems());
        }
        if (from.getUniqueItems() != null) {
            into.setUniqueItems(from.getUniqueItems());
        }
        if (from.getFormat() == null && from.getExample() != null && into.getExample() == null) {
            into.setExample(from.getExample());
        }
    }

    private void warnOnConflict(String field, Object existing, Object incoming, GeneratorContext ctx) {
        if (existing != null && !existing.equals(incoming)) {
            ctx.warn("allOf merge conflict on '" + field + "': " + existing + " vs " + incoming + " (last wins)");
        }
    }
}
