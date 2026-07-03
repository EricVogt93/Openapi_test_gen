package io.oatg.spi;

import java.util.List;

/**
 * Extension point for pulling acceptance criteria from a ticket system
 * (planned: Jira). The criteria are expected to be Gherkin fragments that can
 * be turned into scenarios and later published as Xray tests.
 *
 * <p>Not implemented in this version; the default returns an empty list.
 */
public interface TicketSource {

    /**
     * @param ticketKey e.g. a Jira issue key like {@code PROJ-123}
     * @return acceptance criteria found on the ticket, empty if none
     */
    List<AcceptanceCriterion> fetchCriteria(String ticketKey);

    /** A single acceptance criterion, ideally already phrased as Gherkin. */
    record AcceptanceCriterion(String ticketKey, String title, String gherkin) {
    }
}
