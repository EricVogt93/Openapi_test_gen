package io.oatg.spec;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.oatg.OatgException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecResolverIT {

    private static final String SPEC_JSON = "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"T\",\"version\":\"1\"},\"paths\":{}}";
    private static final String SPEC_YAML = "openapi: 3.0.3\ninfo:\n  title: T\n  version: '1'\npaths: {}\n";

    private static WireMockServer server;
    private final SpecResolver resolver = new SpecResolver(Duration.ofSeconds(3));

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @BeforeEach
    void reset() {
        server.resetAll();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private String url(String path) {
        return server.baseUrl() + path;
    }

    @Test
    void directJsonSpecUrl() {
        server.stubFor(get(urlPathEqualTo("/v3/api-docs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/v3/api-docs"));
        assertThat(resolved.specLocation()).isEqualTo(url("/v3/api-docs"));
        assertThat(resolved.suggestedBaseUrl()).isEqualTo(server.baseUrl());
        assertThat(resolved.discovered()).isFalse();
    }

    @Test
    void directYamlSpecServedAsTextPlain() {
        server.stubFor(get(urlPathEqualTo("/spec.yaml")).willReturn(aResponse()
                .withHeader("Content-Type", "text/plain").withBody(SPEC_YAML)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/spec.yaml"));
        assertThat(resolved.specLocation()).isEqualTo(url("/spec.yaml"));
        assertThat(resolved.suggestedBaseUrl()).isEqualTo(server.baseUrl());
    }

    @Test
    void swaggerUiWithInitializerJs() {
        server.stubFor(get(urlPathEqualTo("/swagger-ui/index.html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html")
                .withBody("<!DOCTYPE html><html><div id=\"swagger-ui\"></div></html>")));
        server.stubFor(get(urlPathEqualTo("/swagger-ui/swagger-initializer.js")).willReturn(aResponse()
                .withHeader("Content-Type", "application/javascript")
                .withBody("window.ui = SwaggerUIBundle({ url: \"/v3/api-docs\", dom_id: '#swagger-ui' });")));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/swagger-ui/index.html"));
        assertThat(resolved.specLocation()).isEqualTo(url("/v3/api-docs"));
        assertThat(resolved.discovered()).isTrue();
    }

    @Test
    void springdocRedirectAndSwaggerConfigChain() {
        server.stubFor(get(urlEqualTo("/swagger-ui.html"))
                .willReturn(temporaryRedirect("/swagger-ui/index.html")));
        server.stubFor(get(urlPathEqualTo("/swagger-ui/index.html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html")
                .withBody("<!DOCTYPE html><html></html>")));
        server.stubFor(get(urlPathEqualTo("/swagger-ui/swagger-initializer.js")).willReturn(aResponse()
                .withBody("window.ui = SwaggerUIBundle({ configUrl: \"/v3/api-docs/swagger-config\" });")));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs/swagger-config")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"configUrl\":\"/v3/api-docs/swagger-config\",\"url\":\"/v3/api-docs\"}")));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/swagger-ui.html"));
        assertThat(resolved.specLocation()).isEqualTo(url("/v3/api-docs"));
        assertThat(resolved.suggestedBaseUrl()).isEqualTo(server.baseUrl());
    }

    @Test
    void urlsArrayPicksFirstGroup() {
        server.stubFor(get(urlPathEqualTo("/swagger-ui/index.html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html").withBody("<!DOCTYPE html><html></html>")));
        server.stubFor(get(urlPathEqualTo("/swagger-ui/swagger-initializer.js")).willReturn(aResponse()
                .withBody("urls: [{url: \"/v3/api-docs/groupA\", name: \"A\"},"
                        + " {url: \"/v3/api-docs/groupB\", name: \"B\"}],")));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs/groupA")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs/groupB")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/swagger-ui/index.html"));
        assertThat(resolved.specLocation()).isEqualTo(url("/v3/api-docs/groupA"));
    }

    @Test
    void probingFallbackFindsOpenapiJson() {
        server.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html")
                .withBody("<!DOCTYPE html><html><body>Welcome</body></html>")));
        server.stubFor(get(urlPathEqualTo("/openapi.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(server.baseUrl() + "/");
        assertThat(resolved.specLocation()).isEqualTo(url("/openapi.json"));
        assertThat(resolved.discovered()).isTrue();
    }

    @Test
    void queryParamDeepLinkWins() {
        server.stubFor(get(urlPathEqualTo("/swagger-ui/index.html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html").withBody("<!DOCTYPE html><html></html>")));
        server.stubFor(get(urlPathEqualTo("/custom/spec.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved =
                resolver.resolve(url("/swagger-ui/index.html?url=%2Fcustom%2Fspec.json"));
        assertThat(resolved.specLocation()).isEqualTo(url("/custom/spec.json"));
    }

    @Test
    void unconfiguredPetstoreDefaultIsDeprioritized() {
        server.stubFor(get(urlPathEqualTo("/swagger-ui/index.html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html").withBody("<!DOCTYPE html><html></html>")));
        // stock swagger-ui-dist initializer pointing at the public petstore demo
        server.stubFor(get(urlPathEqualTo("/swagger-ui/swagger-initializer.js")).willReturn(aResponse()
                .withBody("window.ui = SwaggerUIBundle({ url: \"https://petstore.swagger.io/v2/swagger.json\" });")));
        server.stubFor(get(urlPathEqualTo("/v3/api-docs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(SPEC_JSON)));

        SpecResolver.Resolved resolved = resolver.resolve(url("/swagger-ui/index.html"));
        assertThat(resolved.specLocation()).isEqualTo(url("/v3/api-docs"));
    }

    @Test
    void nothingFoundListsAllAttempts() {
        server.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html")
                .withBody("<!DOCTYPE html><html><body>nothing here</body></html>")));

        assertThatThrownBy(() -> resolver.resolve(server.baseUrl() + "/"))
                .isInstanceOf(OatgException.class)
                .hasMessageContaining("/v3/api-docs")
                .hasMessageContaining("/openapi.json")
                .hasMessageContaining("Pass the OpenAPI document URL directly");
    }
}
