package io.oatg.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.oatg.gen.DataGenerator;
import io.oatg.gen.GeneratorContext;
import io.oatg.request.auth.AuthConfigurer;
import io.oatg.spec.model.EndpointOperation;
import io.oatg.spi.SchemaContext;
import io.oatg.spi.TestDataEnhancer;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an {@link EndpointOperation} plus generated values into a concrete
 * {@link GeneratedRequest}. Supports the default parameter styles (form/explode
 * for query, simple for path and header); exotic styles are approximated and a
 * warning is recorded.
 */
public final class RequestAssembler {

    private final DataGenerator generator;
    private final AuthConfigurer auth;
    private final TestDataEnhancer enhancer;
    private final Map<String, String> extraHeaders;

    public RequestAssembler(DataGenerator generator, AuthConfigurer auth,
                            TestDataEnhancer enhancer, Map<String, String> extraHeaders) {
        this.generator = generator;
        this.auth = auth;
        this.enhancer = enhancer;
        this.extraHeaders = extraHeaders;
    }

    public GeneratedRequest assemble(EndpointOperation op, GeneratorContext ctx) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> cookies = new LinkedHashMap<>();
        String resolvedPath = op.pathTemplate();

        for (Parameter param : op.parameters()) {
            boolean required = Boolean.TRUE.equals(param.getRequired());
            if (!required && !ctx.includeOptional()) {
                continue;
            }
            if (param.getSchema() == null) {
                ctx.warn("Parameter '" + param.getName() + "' uses content-based serialization; skipped");
                continue;
            }
            JsonNode value = generator.generate(param.getSchema(), ctx);
            value = enhancer.enhance(new SchemaContext(op.operationId(),
                    param.getIn() + ":" + param.getName(), param.getSchema()), value);

            switch (param.getIn()) {
                case "path" -> resolvedPath = resolvedPath.replace(
                        "{" + param.getName() + "}", encodePathSegment(render(value)));
                case "query" -> query.put(param.getName(), queryValues(value));
                case "header" -> headers.put(param.getName(), render(value));
                case "cookie" -> cookies.put(param.getName(), render(value));
                default -> ctx.warn("Unknown parameter location '" + param.getIn() + "' for " + param.getName());
            }
        }

        JsonNode body = null;
        if (op.bodySchema() != null) {
            body = generator.generate(op.bodySchema(), ctx);
            body = enhancer.enhance(new SchemaContext(op.operationId(), "body", op.bodySchema()), body);
        }

        auth.apply(op, headers, query, cookies);
        extraHeaders.forEach(headers::putIfAbsent);

        return new GeneratedRequest(op.operationId(), op.method(), op.pathTemplate(), resolvedPath,
                query, headers, cookies, body,
                body != null ? op.bodyMediaType() : null,
                op.declaredStatuses(), ctx.seed());
    }

    /** Simple-style rendering: primitives as-is, arrays/objects comma-joined. */
    private static String render(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        List<String> parts = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(v -> parts.add(v.asText()));
        } else {
            for (Iterator<Map.Entry<String, JsonNode>> it = value.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                parts.add(e.getKey());
                parts.add(e.getValue().asText());
            }
        }
        return String.join(",", parts);
    }

    /** Form-style with explode (the OpenAPI default): arrays repeat the key. */
    private static List<String> queryValues(JsonNode value) {
        if (value != null && value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(v -> values.add(v.asText()));
            return values;
        }
        return List.of(render(value));
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
