package org.tradelite.client.fred.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire-format projection of a single FRED observation row. Both fields are strings — FRED returns
 * {@code value} as a string (e.g. {@code "0.65"}) and uses the literal {@code "."} sentinel for "no
 * observation today" (weekends, holidays, pending publication). The {@code FredClient} maps these
 * into typed {@link org.tradelite.client.fred.FredObservation} records and filters the sentinel
 * rows at the boundary.
 *
 * <p>The wire format also carries {@code realtime_start} and {@code realtime_end} fields that we
 * don't need — both equal today's date in standard observations queries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FredObservationDto(
        @JsonProperty("date") String date, @JsonProperty("value") String value) {}
