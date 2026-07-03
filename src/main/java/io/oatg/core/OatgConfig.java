package io.oatg.core;

import io.oatg.gen.OptionalPropsMode;
import io.oatg.request.auth.AuthOptions;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolved configuration shared by both commands.
 *
 * @param specLocation   file path or URL of the OpenAPI document
 * @param outDir         output directory (created if missing)
 * @param seed           root seed; per-operation child seeds are derived from it
 * @param includes       path glob filters (empty = include all)
 * @param excludes       path glob filters
 * @param methods        HTTP method filter, upper-case (empty = all)
 * @param optionalProps  handling of optional properties/parameters
 * @param maxDepth       recursion cap for nested schemas
 * @param auth           static credentials
 * @param extraHeaders   additional static headers for every request
 * @param specTimeout    timeout per spec-discovery request
 */
public record OatgConfig(String specLocation,
                         Path outDir,
                         long seed,
                         List<String> includes,
                         List<String> excludes,
                         Set<String> methods,
                         OptionalPropsMode optionalProps,
                         int maxDepth,
                         AuthOptions auth,
                         Map<String, String> extraHeaders,
                         Duration specTimeout) {
}
