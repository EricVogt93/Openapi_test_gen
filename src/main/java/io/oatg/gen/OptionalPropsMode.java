package io.oatg.gen;

/** How optional object properties and optional parameters are treated. */
public enum OptionalPropsMode {
    /** Always include optional properties — maximizes coverage, fully deterministic. */
    ALWAYS,
    /** Only include required properties. */
    NEVER,
    /** Include each optional property with 50% probability (seeded). */
    RANDOM
}
