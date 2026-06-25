package org.tradelite.core;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.fred.FredClient;
import org.tradelite.client.fred.FredObservation;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.repository.TreasuryIndicatorState;
import org.tradelite.repository.TreasuryIndicatorStateRepository;

/**
 * Monitors US Treasury macro signals (yield-curve spreads + real yield + term premium) from FRED
 * and emits a daily Telegram macro report at 08:30 CET MON-FRI. Alerts on yield-curve inversion
 * transitions (the recession-warning signal) are sent as a separate Telegram message before the
 * daily report. Report-only on the macro-context signals (DFII10 real yield, THREEFYTP10 term
 * premium).
 *
 * <p>Four FRED series are tracked:
 *
 * <ul>
 *   <li>{@code T10Y3M} — 10-year minus 3-month spread (primary recession-probability signal).
 *       Classified via {@link YieldCurveSpreadLevel}. Alerts on transitions into/out of {@code
 *       INVERTED} or {@code DEEPLY_INVERTED} bands.
 *   <li>{@code T10Y2Y} — 10-year minus 2-year spread (confirmation signal). Same classification
 *       enum, same alert logic, same band thresholds.
 *   <li>{@code DFII10} — 10-year real yield via TIPS. Classified via {@link RealYieldLevel}.
 *       Report-only (no alerts).
 *   <li>{@code THREEFYTP10} — 10-year term premium (NY Fed ACM model). Classified via {@link
 *       TermPremiumLevel}. Report-only.
 * </ul>
 *
 * <p>FRED publishes the daily series (T10Y3M, T10Y2Y, DFII10) at T+0/T+1; the term premium model
 * output (THREEFYTP10) lags ~5 trading days. We fetch a 7-day window per series and take the most
 * recent non-missing observation. A series-level failure renders as "—" in the daily report and is
 * logged at WARN; the other series still alert and report independently.
 *
 * <p>Per-series state is persisted via {@link TreasuryIndicatorStateRepository}. The first time we
 * observe a series (no row exists), we write {@code initialized=false} and suppress the transition
 * alert — matching the same "no ghost alerts on first deploy" pattern as {@code MomentumRocState}.
 * Subsequent runs see {@code initialized=true} and alert on real transitions.
 *
 * <p>FRED ToS requires attribution. The daily report footer includes:
 *
 * <pre>_Treasury data: FRED® (not endorsed by FRBSL)._</pre>
 *
 * <p>Added in #516.
 */
@Slf4j
@Component
public class TreasuryTracker {

    static final String SERIES_T10Y3M = "T10Y3M";
    static final String SERIES_T10Y2Y = "T10Y2Y";
    static final String SERIES_DFII10 = "DFII10";
    static final String SERIES_THREEFYTP10 = "THREEFYTP10";

    /**
     * Days back from "today" we ask FRED for. 7 covers the typical T+5 lag of the weekly
     * THREEFYTP10 with margin for federal holidays / weekends.
     */
    private static final int FETCH_WINDOW_DAYS = 7;

    private static final String MISSING_PLACEHOLDER = "—";
    private static final String FRED_ATTRIBUTION =
            "_Treasury data: FRED® (not endorsed by FRBSL)._";

    private final FredClient fredClient;
    private final TelegramGateway telegramClient;
    private final TreasuryIndicatorStateRepository stateRepository;
    private final Clock clock;

    @Autowired
    public TreasuryTracker(
            FredClient fredClient,
            TelegramGateway telegramClient,
            TreasuryIndicatorStateRepository stateRepository) {
        this(fredClient, telegramClient, stateRepository, Clock.systemDefaultZone());
    }

    /** Constructor for tests — accepts a fixed clock for deterministic timestamps. */
    TreasuryTracker(
            FredClient fredClient,
            TelegramGateway telegramClient,
            TreasuryIndicatorStateRepository stateRepository,
            Clock clock) {
        this.fredClient = fredClient;
        this.telegramClient = telegramClient;
        this.stateRepository = stateRepository;
        this.clock = clock;
    }

    /**
     * Fetches latest values for all four series, sends transition alerts (if any), then the daily
     * report. Idempotent in the sense that re-running on the same FRED data is a no-op for state
     * writes and produces no duplicate alerts.
     */
    public void checkAndAlert() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(FETCH_WINDOW_DAYS);

        Optional<FredObservation> t10y3m = latestObservation(SERIES_T10Y3M, from, today);
        Optional<FredObservation> t10y2y = latestObservation(SERIES_T10Y2Y, from, today);
        Optional<FredObservation> dfii10 = latestObservation(SERIES_DFII10, from, today);
        Optional<FredObservation> tp10 = latestObservation(SERIES_THREEFYTP10, from, today);

        // Transition alerts: only the two spread series. Each alert is a separate Telegram
        // message; both might fire on the same day if both spreads cross the threshold.
        emitSpreadTransitionAlertIfNeeded(SERIES_T10Y3M, "10Y−3M spread", t10y3m);
        emitSpreadTransitionAlertIfNeeded(SERIES_T10Y2Y, "10Y−2Y spread", t10y2y);

        // Macro-context signals: persist state for symmetry / future-proofing, no alerts.
        persistRealYieldState(dfii10);
        persistTermPremiumState(tp10);

        // Daily report bundles all four signals.
        telegramClient.sendMessage(buildDailyReport(today, t10y3m, t10y2y, dfii10, tp10));
    }

    /** Fetch the most recent non-missing observation for a series. */
    private Optional<FredObservation> latestObservation(
            String seriesId, LocalDate from, LocalDate to) {
        List<FredObservation> observations = fredClient.fetchObservations(seriesId, from, to);
        if (observations.isEmpty()) {
            return Optional.empty();
        }
        // FRED returns rows in date-desc order (sort_order=desc URL param); after filtering
        // missing-value rows the first element is the most recent valid observation.
        return Optional.of(observations.getFirst());
    }

    /**
     * Compares today's spread classification against persisted state. Emits a Telegram alert and
     * writes new state on every transition involving {@code INVERTED} / {@code DEEPLY_INVERTED}.
     * No-op on first observation (suppresses ghost alerts after a fresh deploy).
     */
    private void emitSpreadTransitionAlertIfNeeded(
            String seriesId, String displayName, Optional<FredObservation> latest) {
        if (latest.isEmpty()) {
            log.warn("No fresh observation for {} — skipping transition check", seriesId);
            return;
        }
        FredObservation obs = latest.get();
        YieldCurveSpreadLevel today = YieldCurveSpreadLevel.fromSpread(obs.value());

        Optional<TreasuryIndicatorState> prior = stateRepository.findBySeriesId(seriesId);

        // First observation: write baseline, no alert.
        if (prior.isEmpty() || !prior.get().initialized()) {
            stateRepository.save(
                    new TreasuryIndicatorState(
                            seriesId,
                            today.name(),
                            obs.value(),
                            obs.date(),
                            true,
                            clock.instant()));
            log.info(
                    "Initialized {} state at band={} value={} date={}",
                    seriesId,
                    today,
                    obs.value(),
                    obs.date());
            return;
        }

        YieldCurveSpreadLevel previousBand;
        try {
            previousBand = YieldCurveSpreadLevel.valueOf(prior.get().lastBand());
        } catch (IllegalArgumentException _) {
            // Stale state from an older code path with different enum constants — re-baseline.
            log.warn(
                    "Unrecognized stored band {} for {} — re-baselining",
                    prior.get().lastBand(),
                    seriesId);
            stateRepository.save(
                    new TreasuryIndicatorState(
                            seriesId,
                            today.name(),
                            obs.value(),
                            obs.date(),
                            true,
                            clock.instant()));
            return;
        }

        if (previousBand == today) {
            return; // No transition; nothing to do.
        }

        // Transition occurred. Alert only when at least one of the two bands is in the inverted
        // family — i.e., we crossed into, out of, between, or within the inversion regime.
        // FLAT ↔ NORMAL ↔ STEEP transitions are routine yield-curve fluctuations and don't merit
        // a separate alert (the daily report still shows the new band).
        if (previousBand.isInverted() || today.isInverted()) {
            telegramClient.sendMessage(buildTransitionAlert(displayName, previousBand, today, obs));
        }

        stateRepository.save(
                new TreasuryIndicatorState(
                        seriesId, today.name(), obs.value(), obs.date(), true, clock.instant()));
    }

    /** Build the Telegram alert message for a yield-curve transition. */
    private String buildTransitionAlert(
            String displayName,
            YieldCurveSpreadLevel previousBand,
            YieldCurveSpreadLevel newBand,
            FredObservation obs) {
        String headline;
        if (!previousBand.isInverted() && newBand.isInverted()) {
            headline = "🚨 *Yield-curve inversion entered*";
        } else if (previousBand.isInverted() && !newBand.isInverted()) {
            headline = "✅ *Yield-curve inversion exited*";
        } else if (previousBand == YieldCurveSpreadLevel.INVERTED
                && newBand == YieldCurveSpreadLevel.DEEPLY_INVERTED) {
            headline = "⚠️ *Yield-curve inversion deepened*";
        } else {
            // DEEPLY_INVERTED → INVERTED
            headline = "↘️ *Yield-curve inversion shallowed*";
        }
        return String.format(
                "%s%n%s: %+.2f%% %s (%s → %s)",
                headline, displayName, obs.value(), newBand.getEmoji(), previousBand, newBand);
    }

    private void persistRealYieldState(Optional<FredObservation> latest) {
        latest.ifPresent(
                obs ->
                        stateRepository.save(
                                new TreasuryIndicatorState(
                                        SERIES_DFII10,
                                        RealYieldLevel.fromYield(obs.value()).name(),
                                        obs.value(),
                                        obs.date(),
                                        true,
                                        clock.instant())));
    }

    private void persistTermPremiumState(Optional<FredObservation> latest) {
        latest.ifPresent(
                obs ->
                        stateRepository.save(
                                new TreasuryIndicatorState(
                                        SERIES_THREEFYTP10,
                                        TermPremiumLevel.fromPremium(obs.value()).name(),
                                        obs.value(),
                                        obs.date(),
                                        true,
                                        clock.instant())));
    }

    /** Build the daily macro report. */
    String buildDailyReport(
            LocalDate today,
            Optional<FredObservation> t10y3m,
            Optional<FredObservation> t10y2y,
            Optional<FredObservation> dfii10,
            Optional<FredObservation> tp10) {
        StringBuilder sb = new StringBuilder();
        sb.append("*US Treasury Macro*").append('\n');
        sb.append('_').append(today).append('_').append("\n\n");

        sb.append("*Yield curve*").append('\n');
        sb.append(formatSpreadLine("10Y−3M spread", t10y3m)).append('\n');
        sb.append(formatSpreadLine("10Y−2Y spread", t10y2y)).append('\n');

        sb.append('\n').append("*Macro context*").append('\n');
        sb.append(formatRealYieldLine("10Y real yield (DFII10)", dfii10)).append('\n');
        sb.append(formatTermPremiumLine("10Y term premium", tp10)).append('\n');

        sb.append('\n').append(FRED_ATTRIBUTION);
        return sb.toString();
    }

    private String formatSpreadLine(String label, Optional<FredObservation> obs) {
        if (obs.isEmpty()) {
            return MISSING_PLACEHOLDER + " " + label + ": —";
        }
        double value = obs.get().value();
        YieldCurveSpreadLevel band = YieldCurveSpreadLevel.fromSpread(value);
        return String.format("%s %s: %+.2f%% (%s)", band.getEmoji(), label, value, band);
    }

    private String formatRealYieldLine(String label, Optional<FredObservation> obs) {
        if (obs.isEmpty()) {
            return MISSING_PLACEHOLDER + " " + label + ": —";
        }
        double value = obs.get().value();
        RealYieldLevel band = RealYieldLevel.fromYield(value);
        return String.format("%s %s: %.2f%% (%s)", band.getEmoji(), label, value, band);
    }

    private String formatTermPremiumLine(String label, Optional<FredObservation> obs) {
        if (obs.isEmpty()) {
            return MISSING_PLACEHOLDER + " " + label + ": —";
        }
        double value = obs.get().value();
        // Use 4 decimals on the value (FRED returns it that way) but display 2 to match the
        // other indicators. Caller never inspects exact representation, only readability.
        TermPremiumLevel band = TermPremiumLevel.fromPremium(value);
        return String.format("%s %s: %.2f%% (%s)", band.getEmoji(), label, value, band);
    }
}
