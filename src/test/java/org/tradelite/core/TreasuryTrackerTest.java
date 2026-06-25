package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.fred.FredClient;
import org.tradelite.client.fred.FredObservation;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.repository.TreasuryIndicatorState;
import org.tradelite.repository.TreasuryIndicatorStateRepository;

@ExtendWith(MockitoExtension.class)
class TreasuryTrackerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 23);
    private static final Instant NOW = TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant();
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    @Mock private FredClient fredClient;
    @Mock private TelegramGateway telegramClient;
    @Mock private TreasuryIndicatorStateRepository stateRepository;

    private TreasuryTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TreasuryTracker(fredClient, telegramClient, stateRepository, FIXED_CLOCK);
    }

    // ---------- First-run behavior: no ghost alerts ----------

    @Test
    void checkAndAlert_firstRunWithInversionPresent_writesStateButDoesNotAlertOnTransition() {
        // T10Y3M shows INVERTED on first observation. No prior state. We must NOT emit a
        // transition alert; we should write initialized=true state and still send the daily report.
        stubFredFetch("T10Y3M", -0.20);
        stubFredFetch("T10Y2Y", 0.34);
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId(anyString())).thenReturn(Optional.empty());

        tracker.checkAndAlert();

        // No transition alert messages.
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(messageCaptor.capture());
        String onlyMessage = messageCaptor.getValue();
        assertThat(onlyMessage)
                .as("Only the daily report should be sent on first run")
                .startsWith("*US Treasury Macro*");
        assertThat(onlyMessage).doesNotContain("inversion entered");

        // Four state writes — all initialized=true so future runs treat today as the baseline.
        verify(stateRepository, times(4)).save(any(TreasuryIndicatorState.class));
    }

    // ---------- Transition: FLAT → INVERTED triggers alert ----------

    @Test
    void checkAndAlert_t10y3mEntersInversion_sendsAlertBeforeDailyReport() {
        stubFredFetch("T10Y3M", -0.20); // INVERTED
        stubFredFetch("T10Y2Y", 0.34); // FLAT
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "FLAT",
                                        0.10,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.34,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(2)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages.get(0)).contains("inversion entered").contains("10Y−3M spread");
        assertThat(messages.get(1)).startsWith("*US Treasury Macro*");
    }

    // ---------- Transition: INVERTED → FLAT triggers exit alert ----------

    @Test
    void checkAndAlert_t10y3mExitsInversion_sendsExitAlertBeforeDailyReport() {
        stubFredFetch("T10Y3M", 0.30); // FLAT (out of inversion)
        stubFredFetch("T10Y2Y", 0.34); // FLAT (no change)
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "INVERTED",
                                        -0.20,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.34,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(2)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages.get(0))
                .contains("inversion exited")
                .contains("10Y−3M spread")
                .contains("INVERTED → FLAT");
        assertThat(messages.get(1)).startsWith("*US Treasury Macro*");
    }

    // ---------- Transition: INVERTED → DEEPLY_INVERTED triggers deepened alert ----------

    @Test
    void checkAndAlert_inversionDeepens_sendsDeepenedAlert() {
        stubFredFetch("T10Y3M", -0.80); // DEEPLY_INVERTED
        stubFredFetch("T10Y2Y", 0.34);
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "INVERTED",
                                        -0.20,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.34,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(2)).sendMessage(captor.capture());
        assertThat(captor.getAllValues().get(0))
                .contains("deepened")
                .contains("INVERTED → DEEPLY_INVERTED");
    }

    // ---------- Non-inversion transitions: no alert ----------

    @Test
    void checkAndAlert_flatToNormalTransition_doesNotAlert() {
        stubFredFetch("T10Y3M", 0.70); // NORMAL
        stubFredFetch("T10Y2Y", 0.34); // FLAT
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "FLAT",
                                        0.20,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.34,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        // Exactly one message — the daily report. No transition alert.
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @Test
    void checkAndAlert_noTransition_doesNotAlert() {
        stubFredFetch("T10Y3M", 0.65); // NORMAL (same as before)
        stubFredFetch("T10Y2Y", 0.34); // FLAT (same as before)
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "NORMAL",
                                        0.60,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.30,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    // ---------- Macro-context signals do not alert ----------

    @Test
    void checkAndAlert_dfii10BandChange_doesNotTriggerAlert() {
        stubFredFetch("T10Y3M", 0.65); // NORMAL (no change)
        stubFredFetch("T10Y2Y", 0.34); // FLAT (no change)
        stubFredFetch("DFII10", 2.80); // DEEPLY_RESTRICTIVE (changed from RESTRICTIVE)
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y3M"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y3M",
                                        "NORMAL",
                                        0.65,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.34,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        verify(telegramClient, times(1)).sendMessage(anyString()); // only the daily report
    }

    // ---------- Partial-data resilience: T10Y3M unavailable ----------

    @Test
    void checkAndAlert_oneSeriesUnavailable_partialReportRendered() {
        when(fredClient.fetchObservations(eq("T10Y3M"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of()); // T10Y3M unavailable
        stubFredFetch("T10Y2Y", 0.34);
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("10Y−3M spread: —");
        assertThat(report).contains("10Y−2Y spread:"); // T10Y2Y still rendered
        assertThat(report).contains("10Y real yield");
        assertThat(report).contains("10Y term premium");
    }

    @Test
    void checkAndAlert_unavailableSeries_doesNotEmitAlertForThatSeries() {
        when(fredClient.fetchObservations(eq("T10Y3M"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of()); // T10Y3M unavailable — no alert possible
        stubFredFetch("T10Y2Y", -0.30); // T10Y2Y newly inverted → should alert
        stubFredFetch("DFII10", 2.28);
        stubFredFetch("THREEFYTP10", 0.75);
        when(stateRepository.findBySeriesId("T10Y2Y"))
                .thenReturn(
                        Optional.of(
                                new TreasuryIndicatorState(
                                        "T10Y2Y",
                                        "FLAT",
                                        0.10,
                                        TODAY.minusDays(1),
                                        true,
                                        NOW.minusSeconds(86400))));

        tracker.checkAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(2)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        assertThat(messages.get(0)).contains("inversion entered").contains("10Y−2Y spread");
        assertThat(messages.get(0)).doesNotContain("10Y−3M");
        // No state write for unavailable T10Y3M.
        verify(stateRepository, never())
                .save(argTreasury(state -> TreasuryTracker.SERIES_T10Y3M.equals(state.seriesId())));
    }

    // ---------- Report format ----------

    @Test
    void buildDailyReport_containsExpectedSections() {
        Optional<FredObservation> t10y3m =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.65));
        Optional<FredObservation> t10y2y =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.34));
        Optional<FredObservation> dfii10 =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 22), 2.28));
        Optional<FredObservation> tp10 =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 18), 0.75));

        String report = tracker.buildDailyReport(TODAY, t10y3m, t10y2y, dfii10, tp10);

        assertThat(report).startsWith("*US Treasury Macro*\n");
        assertThat(report).contains("_2026-06-23_");
        assertThat(report).contains("*Yield curve*");
        assertThat(report).contains("*Macro context*");
        assertThat(report).contains("*Reading*");
        assertThat(report).contains("10Y−3M spread: +0.65%");
        assertThat(report).contains("10Y−2Y spread: +0.34%");
        assertThat(report).contains("10Y real yield (DFII10): 2.28%");
        assertThat(report).contains("10Y term premium: 0.75%");
        // Layer 3 (composite regime) — TL;DR; today's bands map to "Mid-to-late cycle".
        assertThat(report).contains("Mid-to-late cycle expansion under tight monetary policy.");
        // Layer 1 (curve reading).
        assertThat(report)
                .contains(
                        "The yield curve is normal and the bond market sees no near-term"
                                + " recession risk.");
        // Layer 2 (macro context reading).
        assertThat(report)
                .contains(
                        "Real yields are restrictive — a multi-quarter headwind on equity"
                                + " multiples");
        assertThat(report).contains("FRED®");
        assertThat(report).contains("not endorsed by FRBSL");
        // No flag emoji.
        assertThat(report).doesNotContain("🇺🇸");
    }

    @Test
    void buildDailyReport_inversionShowsAsNegativeWithCorrectEmoji() {
        Optional<FredObservation> t10y3m =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), -0.20));
        Optional<FredObservation> t10y2y =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.34));
        Optional<FredObservation> dfii10 =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 22), 2.28));
        Optional<FredObservation> tp10 =
                Optional.of(new FredObservation(LocalDate.of(2026, 6, 18), 0.75));

        String report = tracker.buildDailyReport(TODAY, t10y3m, t10y2y, dfii10, tp10);

        assertThat(report).contains("🟠 10Y−3M spread: -0.20% (INVERTED)");
    }

    @Test
    void buildDailyReport_allSeriesMissing_rendersAllPlaceholders() {
        String report =
                tracker.buildDailyReport(
                        TODAY,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());

        assertThat(report).contains("10Y−3M spread: —");
        assertThat(report).contains("10Y−2Y spread: —");
        assertThat(report).contains("10Y real yield (DFII10): —");
        assertThat(report).contains("10Y term premium: —");
        // Reading section skipped — generating narrative from missing inputs would be
        // confusing without adding information.
        assertThat(report).doesNotContain("*Reading*");
        // Attribution footer still present even with all-missing data.
        assertThat(report).contains("FRED®");
    }

    @Test
    void buildDailyReport_missingT10Y3M_skipsReadingSection() {
        // T10Y3M drives Layers 1+3; without it the narrative can't be built. Other signals
        // are still rendered as data lines.
        String report =
                tracker.buildDailyReport(
                        TODAY,
                        Optional.empty(),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.34)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 22), 2.28)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 18), 0.75)));

        assertThat(report).doesNotContain("*Reading*");
        assertThat(report).contains("10Y−2Y spread: +0.34%");
        assertThat(report).contains("10Y real yield");
    }

    @Test
    void buildDailyReport_missingDFII10_skipsReadingSection() {
        // DFII10 drives Layer 2 and Layer 3's healthy-curve branch. Without it the narrative
        // can't be built deterministically.
        String report =
                tracker.buildDailyReport(
                        TODAY,
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.65)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.34)),
                        Optional.empty(),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 18), 0.75)));

        assertThat(report).doesNotContain("*Reading*");
    }

    @Test
    void buildDailyReport_missingOnlyTermPremium_stillRendersReadingSection() {
        // TP10 is weekly-published and can be missing mid-week. Layer 2 falls back to a NORMAL
        // TP10 default (avoiding the ELEVATED-specific phrasing) so the narrative still renders.
        String report =
                tracker.buildDailyReport(
                        TODAY,
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.65)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), 0.34)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 22), 2.28)),
                        Optional.empty());

        assertThat(report).contains("*Reading*");
        assertThat(report).contains("Mid-to-late cycle expansion under tight monetary policy.");
    }

    @Test
    void buildDailyReport_invertedCurve_narrativeShowsRecessionWarning() {
        // Inverted T10Y3M; the composite-regime sentence should NOT mention "expansion" or
        // "cycle stage" — instead the recession-warning regime takes priority.
        String report =
                tracker.buildDailyReport(
                        TODAY,
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), -0.20)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 23), -0.30)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 22), 2.28)),
                        Optional.of(new FredObservation(LocalDate.of(2026, 6, 18), 0.75)));

        assertThat(report).contains("*Reading*");
        assertThat(report).contains("Recession warning regime");
        assertThat(report)
                .contains(
                        "The yield curve is inverted — the bond market is pricing future Fed"
                                + " rate cuts");
    }

    // ---------- Helpers ----------

    private void stubFredFetch(String seriesId, double value) {
        when(fredClient.fetchObservations(eq(seriesId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new FredObservation(TODAY, value)));
    }

    /** Mockito custom argument matcher for the State record — captures only "matching" rows. */
    private TreasuryIndicatorState argTreasury(
            java.util.function.Predicate<TreasuryIndicatorState> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
