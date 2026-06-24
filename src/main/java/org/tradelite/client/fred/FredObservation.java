package org.tradelite.client.fred;

import java.time.LocalDate;

/**
 * A single observation in a FRED time series after parsing — typed {@code LocalDate} for the date
 * and unboxed {@code double} for the value. {@code FredClient} filters out the {@code "."} sentinel
 * (missing-data marker) at the wire→domain boundary so consumers don't have to worry about it.
 *
 * <p>Returned by {@link FredClient#fetchObservations} in date-descending order (per the {@code
 * sort_order=desc} URL parameter). Consumers typically want {@code list.getFirst()} for the most
 * recent observation.
 */
public record FredObservation(LocalDate date, double value) {}
