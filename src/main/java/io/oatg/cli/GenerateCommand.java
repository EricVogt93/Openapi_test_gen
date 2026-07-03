package io.oatg.cli;

import io.oatg.artifact.GherkinWriter;
import io.oatg.artifact.RequestCollectionWriter;
import io.oatg.core.BaseUrlResolver;
import io.oatg.core.GenerationService;
import io.oatg.core.OatgConfig;
import io.oatg.spi.Extensions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Generates request-collection and Gherkin artifacts without touching any server. */
@Command(name = "generate", mixinStandardHelpOptions = true,
        description = "Generate request collection and Gherkin features from an OpenAPI spec.")
public final class GenerateCommand implements Callable<Integer> {

    @Mixin
    SharedOptions shared;

    @Option(names = "--formats", split = ",", defaultValue = "collection,gherkin",
            paramLabel = "<collection|gherkin>",
            description = "Artifacts to produce (default: ${DEFAULT-VALUE})")
    List<String> formats;

    @Option(names = "--gherkin-group", defaultValue = "tag", paramLabel = "<tag|path>",
            description = "Group scenarios into feature files by first tag or first path segment (default: ${DEFAULT-VALUE})")
    String gherkinGroup;

    @Option(names = "--base-url", paramLabel = "<url>",
            description = "Base URL recorded in the collection (default: first server from the spec)")
    String baseUrl;

    private final Extensions extensions = Extensions.defaults();

    @Override
    public Integer call() {
        long seed = shared.resolveSeed();
        System.out.println("oatg generate — seed " + seed + " (pass --seed " + seed + " to reproduce)");

        OatgConfig config = shared.toConfig(seed);
        GenerationService.Outcome outcome = new GenerationService(extensions).prepare(config);
        SharedOptions.printDiscovery(outcome, shared.spec);

        BaseUrlResolver.BaseUrl resolved =
                BaseUrlResolver.resolve(baseUrl, outcome.api(), outcome.suggestedBaseUrl());
        String effectiveBaseUrl = resolved != null ? resolved.url() : null;
        if (baseUrl == null && resolved != null) {
            System.out.println("Using base URL " + resolved.url() + " (" + resolved.source() + ")");
        }

        if (formats.contains("collection")) {
            Path file = new RequestCollectionWriter()
                    .write(outcome.api(), outcome.requests(), seed, effectiveBaseUrl, config.outDir());
            System.out.println("Wrote " + file + " (" + outcome.requests().size() + " requests)");
        }
        if (formats.contains("gherkin")) {
            GherkinWriter.GroupMode mode = GherkinWriter.GroupMode
                    .valueOf(gherkinGroup.toUpperCase(Locale.ROOT));
            GherkinWriter.Result result = new GherkinWriter().write(
                    outcome.api(), outcome.requests(), outcome.tagsByOperationId(), mode, seed, config.outDir());
            System.out.println("Wrote " + result.files().size() + " feature file(s) to "
                    + config.outDir().resolve("features"));
            extensions.testManagementPublisher().publish(result.scenarios(), null);
        }

        outcome.skipped().forEach(s -> System.out.println(
                "SKIPPED " + s.method() + " " + s.pathTemplate() + " — " + s.reason()));
        if (!outcome.warnings().isEmpty()) {
            System.out.println(outcome.warnings().size() + " generation warning(s); run with -v for details");
        }
        return 0;
    }
}
