package org.tradelite.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.client.finviz.dto.IndustryPerformance;

class SectorRotationAnalyzerTest {

    private SectorRotationAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SectorRotationAnalyzer();
    }

    @Test
    void analyzeRotations_withNullHistory_returnsEmptyList() {
        List<RotationSignal> signals = analyzer.analyzeRotations(null);
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withEmptyHistory_returnsEmptyList() {
        List<RotationSignal> signals = analyzer.analyzeRotations(List.of());
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withInsufficientHistory_returnsEmptyList() {
        // Only 3 snapshots, need at least 5
        List<SectorPerformanceSnapshot> history = createHistoryWithSnapshots(3, "Tech", 5.0, 10.0);
        List<RotationSignal> signals = analyzer.analyzeRotations(history);
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withSufficientHistory_noSignificantDeviation_returnsEmptyList() {
        // Create 6 snapshots with consistent performance (no outliers)
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<IndustryPerformance> perfs =
                    List.of(createPerformance("Tech", 5.0 + i * 0.1, 10.0 + i * 0.1));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }

        List<RotationSignal> signals = analyzer.analyzeRotations(history);
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withSignificantPositiveDeviation_detectsRotatingIn() {
        // Create history with normal performance
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs = List.of(createPerformance("Tech", 2.0, 5.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        // Add latest snapshot with extreme positive performance (> 2 std dev)
        List<IndustryPerformance> latestPerfs = List.of(createPerformance("Tech", 15.0, 25.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        assertThat(signals, hasSize(1));
        assertThat(signals.getFirst().sectorName(), is("Tech"));
        assertThat(signals.getFirst().signalType(), is(RotationSignal.SignalType.ROTATING_IN));
        assertThat(signals.getFirst().confidence(), is(RotationSignal.Confidence.HIGH));
    }

    @Test
    void analyzeRotations_withSignificantNegativeDeviation_detectsRotatingOut() {
        // Create history with normal positive performance
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs = List.of(createPerformance("Energy", 3.0, 8.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        // Add latest snapshot with extreme negative performance
        List<IndustryPerformance> latestPerfs = List.of(createPerformance("Energy", -12.0, -20.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        assertThat(signals, hasSize(1));
        assertThat(signals.getFirst().sectorName(), is("Energy"));
        assertThat(signals.getFirst().signalType(), is(RotationSignal.SignalType.ROTATING_OUT));
        assertThat(signals.getFirst().confidence(), is(RotationSignal.Confidence.HIGH));
    }

    @Test
    void analyzeRotations_withDivergingZScores_returnsNoSignal() {
        // Create history where weekly and monthly have opposite directions
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs = List.of(createPerformance("Healthcare", 2.0, -2.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        // Latest: extreme weekly positive, extreme monthly negative (diverging)
        List<IndustryPerformance> latestPerfs =
                List.of(createPerformance("Healthcare", 15.0, -15.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        // Should not detect signal due to diverging directions
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withMultipleSectors_detectsMultipleSignals() {
        // Create history with multiple sectors
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs =
                    List.of(
                            createPerformance("Tech", 2.0, 5.0),
                            createPerformance("Finance", -1.0, -2.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        // Latest: Tech surging, Finance crashing
        List<IndustryPerformance> latestPerfs =
                List.of(
                        createPerformance("Tech", 18.0, 30.0),
                        createPerformance("Finance", -15.0, -25.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        assertThat(signals, hasSize(2));

        RotationSignal techSignal =
                signals.stream()
                        .filter(s -> s.sectorName().equals("Tech"))
                        .findFirst()
                        .orElse(null);
        assertThat(techSignal, is(notNullValue()));
        assertThat(techSignal.signalType(), is(RotationSignal.SignalType.ROTATING_IN));

        RotationSignal financeSignal =
                signals.stream()
                        .filter(s -> s.sectorName().equals("Finance"))
                        .findFirst()
                        .orElse(null);
        assertThat(financeSignal, is(notNullValue()));
        assertThat(financeSignal.signalType(), is(RotationSignal.SignalType.ROTATING_OUT));
    }

    @Test
    void analyzeRotations_withZeroStandardDeviation_skipsSignal() {
        // Create history where all values are identical (std dev = 0)
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<IndustryPerformance> perfs = List.of(createPerformance("Utilities", 1.0, 2.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        // Should skip because std dev is 0
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_withNullPerformanceValues_handlesGracefully() {
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs =
                    List.of(
                            new IndustryPerformance(
                                    "NullSector", null, null, null, null, null, null, null));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        // Add one more with valid values
        List<IndustryPerformance> latestPerfs =
                List.of(createPerformance("NullSector", 10.0, 20.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        // Should not throw, should return empty (insufficient data with nulls)
        List<RotationSignal> signals = analyzer.analyzeRotations(history);
        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeRotations_signalContainsCorrectPerformanceValues() {
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<IndustryPerformance> perfs = List.of(createPerformance("Consumer", 1.0, 3.0));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(6 - i), perfs));
        }
        List<IndustryPerformance> latestPerfs = List.of(createPerformance("Consumer", 20.0, 40.0));
        history.add(new SectorPerformanceSnapshot(LocalDate.now(), latestPerfs));

        List<RotationSignal> signals = analyzer.analyzeRotations(history);

        assertThat(signals, hasSize(1));
        RotationSignal signal = signals.getFirst();
        assertThat(signal.weeklyPerformance(), is(new BigDecimal("20.0")));
        assertThat(signal.monthlyPerformance(), is(new BigDecimal("40.0")));
        assertThat(signal.zScoreWeekly(), is(greaterThan(2.0)));
        assertThat(signal.zScoreMonthly(), is(greaterThan(2.0)));
    }

    private List<SectorPerformanceSnapshot> createHistoryWithSnapshots(
            int count, String sectorName, double weeklyPerf, double monthlyPerf) {
        List<SectorPerformanceSnapshot> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<IndustryPerformance> perfs =
                    List.of(createPerformance(sectorName, weeklyPerf, monthlyPerf));
            history.add(new SectorPerformanceSnapshot(LocalDate.now().minusDays(count - i), perfs));
        }
        return history;
    }

    private IndustryPerformance createPerformance(
            String name, double weeklyPerf, double monthlyPerf) {
        return new IndustryPerformance(
                name,
                new BigDecimal(String.valueOf(weeklyPerf)),
                new BigDecimal(String.valueOf(monthlyPerf)),
                new BigDecimal("5.0"), // quarterly
                new BigDecimal("10.0"), // half
                new BigDecimal("15.0"), // year
                new BigDecimal("12.0"), // ytd
                new BigDecimal("1.0") // daily change
                );
    }
}
