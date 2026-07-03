package io.oatg.spi.noop;

import io.oatg.spi.TicketSource;

import java.util.List;

/** Default ticket source: no ticket system connected. */
public final class NoOpTicketSource implements TicketSource {

    @Override
    public List<AcceptanceCriterion> fetchCriteria(String ticketKey) {
        return List.of();
    }
}
