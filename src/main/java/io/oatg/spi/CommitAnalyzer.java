package io.oatg.spi;

import java.util.List;

/**
 * Extension point for analyzing version-control changes (planned: GitLab) to
 * find out which API endpoints a change set touched, so tests can be generated
 * only for the affected operations.
 *
 * <p>Not implemented in this version; the default returns an empty list.
 */
public interface CommitAnalyzer {

    /**
     * @param repoUrl repository to analyze
     * @param fromRef base ref (e.g. target branch or previous release tag)
     * @param toRef   head ref (e.g. feature branch or commit SHA)
     * @return endpoints that were touched between the two refs
     */
    List<ChangedEndpoint> analyze(String repoUrl, String fromRef, String toRef);

    /** An endpoint affected by a change set. */
    record ChangedEndpoint(String method, String pathTemplate, ChangeType changeType) {
    }

    enum ChangeType { ADDED, MODIFIED, REMOVED }
}
