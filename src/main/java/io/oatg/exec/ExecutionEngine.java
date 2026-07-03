package io.oatg.exec;

import io.oatg.OatgException;
import io.oatg.request.GeneratedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Fires generated requests with the JDK HttpClient. Sequential by default;
 * bounded concurrency via a fixed thread pool when requested.
 */
public final class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);
    private static final int SNIPPET_LENGTH = 400;

    private final String baseUrl;
    private final Duration timeout;
    private final int concurrency;
    private final ResponseValidator validator;

    public ExecutionEngine(String baseUrl, Duration timeout, int concurrency, ResponseValidator validator) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.concurrency = Math.max(1, concurrency);
        this.validator = validator;
    }

    public List<ExecutionResult> execute(List<GeneratedRequest> requests) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        if (concurrency == 1) {
            List<ExecutionResult> results = new ArrayList<>(requests.size());
            for (GeneratedRequest request : requests) {
                results.add(executeOne(client, request));
            }
            return results;
        }
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<ExecutionResult>> futures = requests.stream()
                    .map(r -> pool.submit(() -> executeOne(client, r)))
                    .toList();
            List<ExecutionResult> results = new ArrayList<>(requests.size());
            for (Future<ExecutionResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OatgException("Execution interrupted", e);
        } catch (ExecutionException e) {
            throw new OatgException("Execution failed: " + e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private ExecutionResult executeOne(HttpClient client, GeneratedRequest request) {
        HttpRequest httpRequest;
        try {
            httpRequest = buildHttpRequest(request);
        } catch (RuntimeException e) {
            return new ExecutionResult(request, ExecutionResult.Verdict.ERROR, null, null,
                    "could not build request: " + e.getMessage(), null);
        }
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            ResponseValidator.Judgement judgement = validator.judge(response, request.expectedStatuses());
            log.debug("{} {} -> {} ({} ms)", request.method(), request.resolvedPath(),
                    response.statusCode(), latencyMs);
            return new ExecutionResult(request, judgement.verdict(), response.statusCode(),
                    latencyMs, judgement.reason(), snippet(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(request, ExecutionResult.Verdict.ERROR, null, null, "interrupted", null);
        } catch (Exception e) {
            return new ExecutionResult(request, ExecutionResult.Verdict.ERROR, null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    HttpRequest buildHttpRequest(GeneratedRequest request) {
        URI uri = URI.create(baseUrl + request.resolvedPath() + queryString(request.queryParams()));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);

        HttpRequest.BodyPublisher body = request.body() != null
                ? HttpRequest.BodyPublishers.ofString(request.body().toString(), StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(request.method(), body);

        if (request.body() != null) {
            builder.header("Content-Type",
                    request.bodyMediaType() != null ? request.bodyMediaType() : "application/json");
        }
        request.headers().forEach(builder::header);
        if (!request.cookies().isEmpty()) {
            builder.header("Cookie", request.cookies().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        builder.header("Accept", "application/json, */*");
        return builder.build();
    }

    private static String queryString(Map<String, List<String>> params) {
        if (params.isEmpty()) {
            return "";
        }
        return "?" + params.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(v -> encode(e.getKey()) + "=" + encode(v)))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String snippet(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return body.length() <= SNIPPET_LENGTH ? body : body.substring(0, SNIPPET_LENGTH) + "…";
    }
}
