package io.oatg.exec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

/**
 * Decides the verdict for a response. PASS means the status code is declared
 * in the spec (exact match, range pattern like {@code 2XX}, or {@code default}).
 */
public final class ResponseValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean failOn5xx;
    private final boolean validateResponse;

    public ResponseValidator(boolean failOn5xx, boolean validateResponse) {
        this.failOn5xx = failOn5xx;
        this.validateResponse = validateResponse;
    }

    public record Judgement(ExecutionResult.Verdict verdict, String reason) {
    }

    public Judgement judge(HttpResponse<String> response, List<String> declaredStatuses) {
        int status = response.statusCode();
        if (failOn5xx && status >= 500) {
            return new Judgement(ExecutionResult.Verdict.FAIL, "server error " + status + " (--fail-on-5xx)");
        }
        if (!isDeclared(status, declaredStatuses)) {
            return new Judgement(ExecutionResult.Verdict.FAIL,
                    "status " + status + " not declared in spec (declared: " + declaredStatuses + ")");
        }
        if (validateResponse) {
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String body = response.body();
            if (contentType.toLowerCase(Locale.ROOT).contains("json") && body != null && !body.isBlank()) {
                try {
                    MAPPER.readTree(body);
                } catch (JsonProcessingException e) {
                    return new Judgement(ExecutionResult.Verdict.FAIL,
                            "response declares JSON but body is not parseable: " + e.getOriginalMessage());
                }
            }
        }
        return new Judgement(ExecutionResult.Verdict.PASS, null);
    }

    static boolean isDeclared(int status, List<String> declared) {
        String exact = String.valueOf(status);
        String range = (status / 100) + "XX";
        for (String d : declared) {
            if (d.equals(exact) || d.equalsIgnoreCase(range) || d.equalsIgnoreCase("default")) {
                return true;
            }
        }
        return false;
    }
}
