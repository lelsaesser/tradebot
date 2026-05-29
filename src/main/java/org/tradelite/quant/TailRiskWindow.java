package org.tradelite.quant;

/**
 * Specifies a tail-risk analysis window. Couples the calendar lookback used to fetch price data
 * with the minimum row count required for the analysis to be valid. The two values must move
 * together: the calendar lookback is oversized so that the database query returns at least {@code
 * minDataPoints} rows year-round (accounting for weekends and market holidays), while {@code
 * minDataPoints} is the actual statistical sample size used by kurtosis and skewness.
 *
 * <p>Canonical instances live as static fields on {@link TailRiskTracker}.
 */
public record TailRiskWindow(int lookbackCalendarDays, int minDataPoints) {}
