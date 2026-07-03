package io.oatg.spec;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecResolverTest {

    @Test
    void extractsUrlAndConfigUrlWithBothQuoteStyles() {
        String js = """
                window.ui = SwaggerUIBundle({
                  configUrl: '/v3/api-docs/swagger-config',
                  url: "/v3/api-docs",
                  dom_id: '#swagger-ui',
                });
                """;
        assertThat(SpecResolver.extractUrlCandidates(js))
                .containsExactly("/v3/api-docs/swagger-config", "/v3/api-docs");
    }

    @Test
    void extractsAllEntriesFromUrlsArray() {
        String js = "urls: [{url: \"/v3/api-docs/groupA\", name: \"A\"}, {url: '/v3/api-docs/groupB', name: 'B'}]";
        assertThat(SpecResolver.extractUrlCandidates(js))
                .containsExactly("/v3/api-docs/groupA", "/v3/api-docs/groupB");
    }

    @Test
    void swaggerConfigJsonYieldsUrlAndUrls() {
        String config = """
                {"configUrl":"/v3/api-docs/swagger-config",
                 "url":"/v3/api-docs",
                 "urls":[{"url":"/v3/api-docs/users","name":"users"}]}
                """;
        assertThat(SpecResolver.candidatesFromSwaggerConfig(config))
                .containsExactly("/v3/api-docs", "/v3/api-docs/users");
    }

    @Test
    void sniffsJsonSpec() {
        assertThat(SpecResolver.looksLikeOpenApiDocument("{\"openapi\":\"3.0.3\",\"paths\":{}}")).isTrue();
        assertThat(SpecResolver.looksLikeOpenApiDocument("{\"swagger\":\"2.0\"}")).isTrue();
        assertThat(SpecResolver.looksLikeOpenApiDocument("{\"urls\":[{\"url\":\"/x\"}]}")).isFalse();
    }

    @Test
    void sniffsYamlSpecEvenWithCommentsAndBom() {
        String yaml = "﻿# generated\n\nopenapi: 3.0.3\ninfo:\n  title: X\n";
        assertThat(SpecResolver.looksLikeOpenApiDocument(yaml)).isTrue();
        assertThat(SpecResolver.looksLikeOpenApiDocument("key: value\nother: 1\n")).isFalse();
    }

    @Test
    void htmlErrorPageIsNotASpec() {
        String html = "<!DOCTYPE html><html><body>openapi: docs here</body></html>";
        assertThat(SpecResolver.looksLikeOpenApiDocument(html)).isFalse();
        assertThat(SpecResolver.looksLikeHtml("text/html", html)).isTrue();
        assertThat(SpecResolver.looksLikeHtml("application/json", html)).isTrue(); // content wins
    }

    @Test
    void originIncludesPortOnlyWhenExplicit() {
        assertThat(SpecResolver.origin(URI.create("http://host:8080/swagger-ui/index.html")))
                .isEqualTo("http://host:8080");
        assertThat(SpecResolver.origin(URI.create("https://api.example.com/docs")))
                .isEqualTo("https://api.example.com");
    }

    @Test
    void uiParentPathStripsUiSegments() {
        assertThat(SpecResolver.uiParentPath(URI.create("http://h/myapp/swagger-ui/index.html")))
                .isEqualTo("/myapp/");
        assertThat(SpecResolver.uiParentPath(URI.create("http://h/myapp/swagger-ui.html")))
                .isEqualTo("/myapp/");
        assertThat(SpecResolver.uiParentPath(URI.create("http://h/"))).isEqualTo("/");
    }

    @Test
    void queryParamDeepLinksAreDecoded() {
        URI uri = URI.create("http://h/swagger-ui/index.html?url=%2Fcustom%2Fspec.json&foo=bar");
        assertThat(SpecResolver.queryParamCandidates(uri)).containsExactly("/custom/spec.json");
    }

    @Test
    void wellKnownCandidatesCoverOriginAndContextPath() {
        SpecResolver resolver = new SpecResolver(Duration.ofSeconds(1));
        List<URI> candidates = resolver.wellKnownCandidates(
                URI.create("http://h:1/myapp/swagger-ui/index.html"));
        assertThat(candidates).contains(
                URI.create("http://h:1/myapp/v3/api-docs"),
                URI.create("http://h:1/v3/api-docs"),
                URI.create("http://h:1/openapi.json"),
                URI.create("http://h:1/q/openapi"));
    }

    @Test
    void localFilePassesThroughUntouched() {
        SpecResolver resolver = new SpecResolver(Duration.ofSeconds(1));
        SpecResolver.Resolved resolved = resolver.resolve("specs/petstore.yaml");
        assertThat(resolved.specLocation()).isEqualTo("specs/petstore.yaml");
        assertThat(resolved.suggestedBaseUrl()).isNull();
        assertThat(resolved.discovered()).isFalse();
    }
}
