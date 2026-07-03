package io.oatg.spi;

import java.util.List;

/**
 * Extension point for publishing generated scenarios to a test management
 * system (planned: Jira Xray — create/update test issues from Gherkin).
 *
 * <p>Not implemented in this version; the default does nothing.
 */
public interface TestManagementPublisher {

    /**
     * @param scenarios generated Gherkin scenarios, tagged with operation id and seed
     * @param runSummary summary of an execution run, or {@code null} when only
     *                   artifacts were generated
     */
    void publish(List<GeneratedScenario> scenarios, RunSummary runSummary);

    /** One generated Gherkin scenario. */
    record GeneratedScenario(String operationId, String featureName, String gherkinSource, List<String> tags) {
    }

    /** Aggregate result of an execution run. */
    record RunSummary(int total, int passed, int failed, int errors, int skipped, long seed) {
    }
}
