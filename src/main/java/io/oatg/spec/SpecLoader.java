package io.oatg.spec;

import io.oatg.OatgException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads an OpenAPI 3.0/3.1 document (YAML or JSON) from a file path or URL and
 * fully resolves {@code $ref}s so downstream code can work on plain schemas.
 */
public final class SpecLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecLoader.class);

    public OpenAPI load(String location) {
        if (!looksLikeUrl(location) && !Files.exists(Path.of(location))) {
            throw new OatgException("Spec not found: " + location);
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(location, null, options);
        if (result.getMessages() != null) {
            result.getMessages().forEach(m -> log.warn("Spec parser: {}", m));
        }
        OpenAPI api = result.getOpenAPI();
        if (api == null) {
            throw new OatgException("Could not parse OpenAPI spec at " + location
                    + (result.getMessages() == null || result.getMessages().isEmpty()
                       ? "" : " — " + String.join("; ", result.getMessages())));
        }
        if (api.getPaths() == null || api.getPaths().isEmpty()) {
            throw new OatgException("Spec contains no paths: " + location);
        }
        return api;
    }

    private static boolean looksLikeUrl(String location) {
        return location.startsWith("http://") || location.startsWith("https://");
    }
}
