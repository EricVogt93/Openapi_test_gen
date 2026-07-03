package io.oatg.exec;

import io.oatg.request.GeneratedRequest;

/**
 * Outcome of firing one generated request.
 *
 * @param request       the request that was sent
 * @param verdict       PASS/FAIL/ERROR
 * @param statusCode    HTTP status, or {@code null} when no response was received
 * @param latencyMs     round-trip time, or {@code null} on transport errors
 * @param reason        human-readable explanation for FAIL/ERROR, else {@code null}
 * @param responseSnippet truncated response body for the report, may be {@code null}
 */
public record ExecutionResult(GeneratedRequest request,
                              Verdict verdict,
                              Integer statusCode,
                              Long latencyMs,
                              String reason,
                              String responseSnippet) {

    public enum Verdict { PASS, FAIL, ERROR }
}
