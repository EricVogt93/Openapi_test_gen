package io.oatg.spi;

import io.oatg.spi.noop.NoOpCommitAnalyzer;
import io.oatg.spi.noop.NoOpTestDataEnhancer;
import io.oatg.spi.noop.NoOpTestManagementPublisher;
import io.oatg.spi.noop.NoOpTicketSource;

import java.util.Objects;

/**
 * Holder for all extension points. Core code only ever talks to the
 * interfaces; concrete adapters (Jira, GitLab, Xray, AI) are wired here later.
 */
public record Extensions(TestDataEnhancer testDataEnhancer,
                         TicketSource ticketSource,
                         CommitAnalyzer commitAnalyzer,
                         TestManagementPublisher testManagementPublisher) {

    public Extensions {
        Objects.requireNonNull(testDataEnhancer);
        Objects.requireNonNull(ticketSource);
        Objects.requireNonNull(commitAnalyzer);
        Objects.requireNonNull(testManagementPublisher);
    }

    public static Extensions defaults() {
        return new Extensions(new NoOpTestDataEnhancer(), new NoOpTicketSource(),
                new NoOpCommitAnalyzer(), new NoOpTestManagementPublisher());
    }
}
