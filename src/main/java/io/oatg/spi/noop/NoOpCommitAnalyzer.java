package io.oatg.spi.noop;

import io.oatg.spi.CommitAnalyzer;

import java.util.List;

/** Default commit analyzer: no VCS connected. */
public final class NoOpCommitAnalyzer implements CommitAnalyzer {

    @Override
    public List<ChangedEndpoint> analyze(String repoUrl, String fromRef, String toRef) {
        return List.of();
    }
}
