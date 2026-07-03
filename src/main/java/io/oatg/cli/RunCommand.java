package io.oatg.cli;

import io.oatg.OatgException;
import io.oatg.artifact.RequestCollectionWriter;
import io.oatg.core.GenerationService;
import io.oatg.core.OatgConfig;
import io.oatg.exec.ExecutionEngine;
import io.oatg.exec.ExecutionResult;
import io.oatg.exec.ResponseValidator;
import io.oatg.report.HtmlReportWriter;
import io.oatg.report.JsonReportWriter;
import io.oatg.report.ReportModel;
import io.oatg.spi.Extensions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Fires generated requests against a running API and reports pass/fail per operation. */
@Command(name = "run", mixinStandardHelpOptions = true,
        description = "Generate requests and execute them against a running API.")
public final class RunCommand implements Callable<Integer> {

    @Mixin
    SharedOptions shared;

    @Option(names = "--base-url", paramLabel = "<url>",
            description = "Base URL of the running API (default: first server from the spec)")
    String baseUrl;

    @Option(names = "--timeout", defaultValue = "10", paramLabel = "<seconds>",
            description = "Per-request timeout (default: ${DEFAULT-VALUE}s)")
    long timeoutSeconds;

    @Option(names = "--concurrency", defaultValue = "1", paramLabel = "<n>",
            description = "Parallel requests (default: ${DEFAULT-VALUE})")
    int concurrency;

    @Option(names = "--validate-response", description = "Also check content type and JSON well-formedness")
    boolean validateResponse;

    @Option(names = "--fail-on-5xx", description = "Treat any 5xx as FAIL even when declared in the spec")
    boolean failOn5xx;

    @Option(names = "--dry-run", description = "Assemble and print requests without sending them")
    boolean dryRun;

    @Option(names = "--report", split = ",", defaultValue = "json,html", paramLabel = "<json|html>",
            description = "Report formats (default: ${DEFAULT-VALUE})")
    List<String> reportFormats;

    private final Extensions extensions = Extensions.defaults();

    @Override
    public Integer call() {
        long seed = shared.resolveSeed();
        System.out.println("oatg run — seed " + seed + " (pass --seed " + seed + " to reproduce)");

        OatgConfig config = shared.toConfig(seed);
        GenerationService.Outcome outcome = new GenerationService(extensions).prepare(config);

        String effectiveBaseUrl = resolveBaseUrl(outcome);

        // always persist what was (or would be) fired — auditable artifact
        new RequestCollectionWriter().write(outcome.api(), outcome.requests(), seed,
                effectiveBaseUrl, config.outDir());

        if (dryRun) {
            outcome.requests().forEach(r -> {
                System.out.println(r.method() + " " + effectiveBaseUrl + r.resolvedPath());
                if (r.body() != null) {
                    System.out.println("  body: " + r.body());
                }
            });
            outcome.skipped().forEach(s -> System.out.println(
                    "SKIPPED " + s.method() + " " + s.pathTemplate() + " — " + s.reason()));
            System.out.println(outcome.requests().size() + " request(s) assembled (dry run, nothing sent)");
            return 0;
        }

        ExecutionEngine engine = new ExecutionEngine(effectiveBaseUrl,
                Duration.ofSeconds(timeoutSeconds), concurrency,
                new ResponseValidator(failOn5xx, validateResponse));
        List<ExecutionResult> results = engine.execute(outcome.requests());

        ReportModel report = buildReport(outcome, results, effectiveBaseUrl, seed);
        if (reportFormats.contains("json")) {
            System.out.println("Wrote " + new JsonReportWriter().write(report, config.outDir()));
        }
        if (reportFormats.contains("html")) {
            System.out.println("Wrote " + new HtmlReportWriter().write(report, config.outDir()));
        }

        ReportModel.Totals totals = report.totals();
        System.out.printf("Result: %d total, %d passed, %d failed, %d errors, %d skipped%n",
                totals.total(), totals.passed(), totals.failed(), totals.errors(), totals.skipped());
        results.stream()
                .filter(r -> r.verdict() != ExecutionResult.Verdict.PASS)
                .forEach(r -> System.out.println("  " + r.verdict() + " " + r.request().method() + " "
                        + r.request().resolvedPath() + " — " + r.reason()));

        return totals.failed() + totals.errors() > 0 ? 1 : 0;
    }

    private String resolveBaseUrl(GenerationService.Outcome outcome) {
        if (baseUrl != null) {
            return baseUrl;
        }
        if (outcome.api().getServers() != null && !outcome.api().getServers().isEmpty()) {
            String url = outcome.api().getServers().get(0).getUrl();
            if (url != null && url.startsWith("http")) {
                System.out.println("No --base-url given; using first spec server: " + url);
                return url;
            }
        }
        throw new OatgException("No --base-url given and the spec declares no absolute server URL");
    }

    private ReportModel buildReport(GenerationService.Outcome outcome, List<ExecutionResult> results,
                                    String effectiveBaseUrl, long seed) {
        List<ReportModel.Entry> entries = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        int errors = 0;
        for (ExecutionResult r : results) {
            switch (r.verdict()) {
                case PASS -> passed++;
                case FAIL -> failed++;
                case ERROR -> errors++;
            }
            entries.add(new ReportModel.Entry(r.request().operationId(), r.request().method(),
                    r.request().resolvedPath(), r.verdict().name(), r.statusCode(), r.latencyMs(), r.reason()));
        }
        for (var s : outcome.skipped()) {
            entries.add(new ReportModel.Entry(null, s.method(), s.pathTemplate(),
                    "SKIPPED", null, null, s.reason()));
        }
        String title = outcome.api().getInfo() != null && outcome.api().getInfo().getTitle() != null
                ? outcome.api().getInfo().getTitle() : "API";
        return new ReportModel("oatg", title, shared.spec, effectiveBaseUrl, seed,
                Instant.now().toString(),
                new ReportModel.Totals(results.size() + outcome.skipped().size(),
                        passed, failed, errors, outcome.skipped().size()),
                entries);
    }
}
