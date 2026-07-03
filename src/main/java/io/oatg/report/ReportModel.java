package io.oatg.report;

import java.util.List;

/**
 * Serializable run report. Timestamps are ISO-8601 strings to keep the JSON
 * layer dependency-free.
 */
public record ReportModel(String tool,
                          String specTitle,
                          String specLocation,
                          String baseUrl,
                          long seed,
                          String timestamp,
                          Totals totals,
                          List<Entry> entries) {

    public record Totals(int total, int passed, int failed, int errors, int skipped) {
    }

    /**
     * @param verdict PASS, FAIL, ERROR or SKIPPED
     */
    public record Entry(String operationId,
                        String method,
                        String path,
                        String verdict,
                        Integer status,
                        Long latencyMs,
                        String reason) {
    }
}
