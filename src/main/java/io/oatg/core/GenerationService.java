package io.oatg.core;

import io.oatg.gen.DataGenerator;
import io.oatg.gen.GeneratorContext;
import io.oatg.request.GeneratedRequest;
import io.oatg.request.RequestAssembler;
import io.oatg.request.auth.AuthConfigurer;
import io.oatg.spec.OperationExtractor;
import io.oatg.spec.SpecLoader;
import io.oatg.spec.SpecResolver;
import io.oatg.spec.model.EndpointOperation;
import io.oatg.spec.model.SkippedOperation;
import io.oatg.spi.Extensions;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared pipeline: load spec → extract operations → filter → generate data →
 * assemble requests. Used by both the {@code generate} and {@code run} commands.
 */
public final class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final Extensions extensions;

    public GenerationService(Extensions extensions) {
        this.extensions = extensions;
    }

    /**
     * @param specLocation     the resolved spec location (may differ from the
     *                         raw {@code --spec} value after auto-discovery)
     * @param suggestedBaseUrl origin of the discovered spec URL, or {@code null}
     *                         when the spec came from a local file
     */
    public record Outcome(OpenAPI api,
                          long seed,
                          List<GeneratedRequest> requests,
                          Map<String, List<String>> tagsByOperationId,
                          List<SkippedOperation> skipped,
                          List<String> warnings,
                          String specLocation,
                          String suggestedBaseUrl) {
    }

    public Outcome prepare(OatgConfig config) {
        SpecResolver.Resolved resolved =
                new SpecResolver(config.specTimeout()).resolve(config.specLocation());
        OpenAPI api = new SpecLoader().load(resolved.specLocation());
        OperationExtractor.ExtractionResult extraction = new OperationExtractor().extract(api);

        List<SkippedOperation> skipped = new ArrayList<>(extraction.skipped());
        List<EndpointOperation> operations = filter(extraction.operations(), config);

        DataGenerator generator = new DataGenerator();
        AuthConfigurer auth = new AuthConfigurer(api, config.auth());
        RequestAssembler assembler = new RequestAssembler(generator, auth,
                extensions.testDataEnhancer(), config.extraHeaders());

        List<GeneratedRequest> requests = new ArrayList<>();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (EndpointOperation op : operations) {
            long childSeed = childSeed(config.seed(), op.key());
            GeneratorContext ctx = new GeneratorContext(childSeed, config.optionalProps(), config.maxDepth());
            requests.add(assembler.assemble(op, ctx));
            tags.put(op.operationId(), op.tags());
            ctx.warnings().forEach(w -> warnings.add(op.key() + ": " + w));
        }

        warnings.forEach(w -> log.warn("{}", w));
        return new Outcome(api, config.seed(), requests, tags, skipped, warnings,
                resolved.specLocation(), resolved.suggestedBaseUrl());
    }

    /**
     * Derives a stable per-operation seed so adding or removing one endpoint
     * does not shift the generated data of every other endpoint.
     */
    static long childSeed(long rootSeed, String operationKey) {
        long hash = 1125899906842597L; // large prime
        for (int i = 0; i < operationKey.length(); i++) {
            hash = 31 * hash + operationKey.charAt(i);
        }
        return rootSeed ^ hash;
    }

    private static List<EndpointOperation> filter(List<EndpointOperation> operations, OatgConfig config) {
        List<PathMatcher> includes = matchers(config.includes());
        List<PathMatcher> excludes = matchers(config.excludes());
        List<EndpointOperation> filtered = new ArrayList<>();
        for (EndpointOperation op : operations) {
            if (!config.methods().isEmpty() && !config.methods().contains(op.method())) {
                continue;
            }
            Path path = Path.of(op.pathTemplate());
            if (!includes.isEmpty() && includes.stream().noneMatch(m -> m.matches(path))) {
                continue;
            }
            if (excludes.stream().anyMatch(m -> m.matches(path))) {
                continue;
            }
            filtered.add(op);
        }
        return filtered;
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        return globs.stream()
                .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
                .toList();
    }
}
