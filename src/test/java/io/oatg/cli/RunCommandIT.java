package io.oatg.cli;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.oatg.Main;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

class RunCommandIT {

    @TempDir
    Path tempDir;

    private static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private int run(Path out, String... extra) {
        String[] base = {"run",
                "--spec", "src/test/resources/specs/petstore.yaml",
                "--base-url", server.baseUrl(),
                "--out", out.toString(),
                "--seed", "42"};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return Main.buildCommandLine().execute(args);
    }

    @Test
    void allDeclaredResponsesYieldExitZeroAndReports() throws IOException {
        server.resetAll();
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"not found\"}")));
        // 404 is only declared on some operations → mixed run; use dedicated stubs instead
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));

        Path out = tempDir.resolve("ok");
        // listPets/getPet/updatePet declare 200; addPet 201/400; deletePet 204/404; placeOrder 201/default
        int exit = run(out, "--include", "/pets/**", "--include-methods", "GET");
        assertThat(exit).isZero();
        assertThat(out.resolve("report.json")).exists();
        assertThat(out.resolve("report.html")).exists();
        assertThat(out.resolve("requests.json")).exists();

        String report = Files.readString(out.resolve("report.json"));
        assertThat(report).contains("\"verdict\" : \"PASS\"");
        assertThat(report).contains("\"seed\" : 42");
    }

    @Test
    void undeclaredStatusYieldsExitOne() throws IOException {
        server.resetAll();
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(418).withBody("teapot")));

        Path out = tempDir.resolve("fail");
        int exit = run(out, "--include", "/pets", "--include-methods", "GET");
        assertThat(exit).isEqualTo(1);
        String report = Files.readString(out.resolve("report.json"));
        assertThat(report).contains("\"verdict\" : \"FAIL\"");
        assertThat(report).contains("418");
    }

    @Test
    void dryRunSendsNothingAndExitsZero() {
        server.resetAll();
        Path out = tempDir.resolve("dry");
        int exit = run(out, "--dry-run");
        assertThat(exit).isZero();
        assertThat(server.getAllServeEvents()).isEmpty();
        assertThat(out.resolve("requests.json")).exists();
    }

    @Test
    void skippedOperationsAppearInReport() throws IOException {
        server.resetAll();
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));
        Path out = tempDir.resolve("skipped");
        run(out, "--include-methods", "GET");
        String report = Files.readString(out.resolve("report.json"));
        assertThat(report).contains("\"verdict\" : \"SKIPPED\"");
        assertThat(report).contains("/pets/{petId}/photo");
    }
}
