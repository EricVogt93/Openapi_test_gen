package io.oatg.spi.noop;

import io.oatg.spi.TestManagementPublisher;

import java.util.List;

/** Default publisher: scenarios stay on disk, nothing is pushed anywhere. */
public final class NoOpTestManagementPublisher implements TestManagementPublisher {

    @Override
    public void publish(List<GeneratedScenario> scenarios, RunSummary runSummary) {
        // intentionally empty
    }
}
