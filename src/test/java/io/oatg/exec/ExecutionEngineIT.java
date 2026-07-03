package io.oatg.exec;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.oatg.request.GeneratedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineIT {

    private static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo("/pets"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
        server.stubFor(post(urlPathEqualTo("/pets"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        server.stubFor(get(urlPathEqualTo("/broken-json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not json")));
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private static GeneratedRequest request(String method, String path, List<String> expected) {
        return new GeneratedRequest("op-" + method + path, method, path, path,
                Map.of(), Map.of(), Map.of(), null, null, expected, 42L);
    }

    private ExecutionEngine engine(boolean failOn5xx, boolean validate) {
        return new ExecutionEngine(server.baseUrl(), Duration.ofSeconds(5), 1,
                new ResponseValidator(failOn5xx, validate));
    }

    @Test
    void declaredStatusPasses() {
        List<ExecutionResult> results = engine(false, false)
                .execute(List.of(request("GET", "/pets", List.of("200"))));
        assertThat(results.get(0).verdict()).isEqualTo(ExecutionResult.Verdict.PASS);
        assertThat(results.get(0).statusCode()).isEqualTo(200);
        assertThat(results.get(0).latencyMs()).isNotNull();
    }

    @Test
    void rangeAndDefaultDeclarationsPass() {
        List<ExecutionResult> results = engine(false, false).execute(List.of(
                request("GET", "/pets", List.of("2XX")),
                request("GET", "/pets", List.of("default"))));
        assertThat(results).allSatisfy(r ->
                assertThat(r.verdict()).isEqualTo(ExecutionResult.Verdict.PASS));
    }

    @Test
    void undeclaredStatusFails() {
        List<ExecutionResult> results = engine(false, false)
                .execute(List.of(request("POST", "/pets", List.of("201", "400"))));
        assertThat(results.get(0).verdict()).isEqualTo(ExecutionResult.Verdict.FAIL);
        assertThat(results.get(0).reason()).contains("500").contains("not declared");
    }

    @Test
    void declared5xxFailsWithStrictFlag() {
        List<ExecutionResult> results = engine(true, false)
                .execute(List.of(request("POST", "/pets", List.of("500"))));
        assertThat(results.get(0).verdict()).isEqualTo(ExecutionResult.Verdict.FAIL);
        assertThat(results.get(0).reason()).contains("--fail-on-5xx");
    }

    @Test
    void invalidJsonFailsWhenValidationEnabled() {
        List<ExecutionResult> results = engine(false, true)
                .execute(List.of(request("GET", "/broken-json", List.of("200"))));
        assertThat(results.get(0).verdict()).isEqualTo(ExecutionResult.Verdict.FAIL);
        assertThat(results.get(0).reason()).contains("not parseable");
    }

    @Test
    void unreachableServerIsError() {
        ExecutionEngine unreachable = new ExecutionEngine("http://127.0.0.1:1",
                Duration.ofSeconds(2), 1, new ResponseValidator(false, false));
        List<ExecutionResult> results = unreachable
                .execute(List.of(request("GET", "/pets", List.of("200"))));
        assertThat(results.get(0).verdict()).isEqualTo(ExecutionResult.Verdict.ERROR);
        assertThat(results.get(0).statusCode()).isNull();
    }

    @Test
    void concurrentExecutionKeepsRequestOrder() {
        List<GeneratedRequest> requests = List.of(
                request("GET", "/pets", List.of("200")),
                request("GET", "/pets", List.of("200")),
                request("GET", "/pets", List.of("200")));
        ExecutionEngine parallel = new ExecutionEngine(server.baseUrl(), Duration.ofSeconds(5), 3,
                new ResponseValidator(false, false));
        List<ExecutionResult> results = parallel.execute(requests);
        assertThat(results).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(results.get(i).request()).isSameAs(requests.get(i));
        }
    }
}
