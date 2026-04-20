package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.core.RelativeStrengthSignal;
import org.tradelite.quant.StatisticsUtil;
import org.tradelite.service.model.DailyPrice;

@SuppressWarnings({"SameParameterValue", "ResultOfMethodCallIgnored"})
@ExtendWith(MockitoExtension.class)
class RelativeStrengthServiceTest {

    @Mock private DailyPriceProvider dailyPriceProvider;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private RelativeStrengthService relativeStrengthService;

    private final String rsDataFile = "config/rs-data.json";

    private static final int RS_LOOKBACK_DAYS = 80;

    @BeforeEach
    void setUp() throws IOException {
        new File(rsDataFile).delete();
        relativeStrengthService = new RelativeStrengthService(objectMapper, dailyPriceProvider);
    }

    @Test
    void testCalculateEma_withSufficientData() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            values.add(1.0 + (i * 0.01));
        }

        double ema = StatisticsUtil.calculateEma(values, 50);

        assertThat(ema, is(greaterThan(1.0)));
        assertThat(ema, is(lessThan(2.0)));
    }

    @Test
    void testCalculateEma_withExactPeriodData() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            values.add(1.0);
        }

        double ema = StatisticsUtil.calculateEma(values, 50);

        assertThat(ema, is(closeTo(1.0, 0.001)));
    }

    @Test
    void testCalculateEma_withInsufficientData() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            values.add(1.0);
        }

        double ema = StatisticsUtil.calculateEma(values, 50);

        assertEquals(0, ema);
    }

    @Test
    void testCalculateRelativeStrength_skipsBenchmark() {
        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength(
                        RelativeStrengthService.BENCHMARK_SYMBOL, "SPDR S&P 500 ETF");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientStockData() {
        when(dailyPriceProvider.findDailyClosingPrices("NVDA", RS_LOOKBACK_DAYS))
                .thenReturn(List.of());
        when(dailyPriceProvider.findDailyClosingPrices("SPY", RS_LOOKBACK_DAYS))
                .thenReturn(List.of());

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientSpyData() {
        stubDailyPrices("NVDA", constantPrices(60, 500.0));
        when(dailyPriceProvider.findDailyClosingPrices("SPY", RS_LOOKBACK_DAYS))
                .thenReturn(List.of());

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientHistory() {
        // Only 5 days — less than 10 minimum
        stubDailyPrices("SPY", constantPrices(5, 500.0));
        stubDailyPrices("NVDA", constantPrices(5, 600.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_noSignalOnFirstCalculation() {
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
        assertThat(relativeStrengthService.getRsHistory().containsKey("NVDA"), is(true));
        assertThat(relativeStrengthService.getRsHistory().get("NVDA").isInitialized(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_crossoverUp() {
        // First calculation to initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 400.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Manually set previous state to simulate RS being below EMA
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(0.8);
        rsData.setPreviousEma(0.85);

        // Now RS jumps above EMA — use rising prices so latest RS > EMA
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 500.0, 700.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(signal.get().signalType(), is(RelativeStrengthSignal.SignalType.OUTPERFORMING));
        assertThat(signal.get().symbol(), is("NVDA"));
        assertThat(signal.get().displayName(), is("Nvidia"));
    }

    @Test
    void testCalculateRelativeStrength_crossoverDown() {
        // First calculation to initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Manually set previous state to simulate RS being above EMA
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.3);
        rsData.setPreviousEma(1.2);

        // Now RS drops below EMA — use declining prices so latest RS < EMA
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 700.0, 400.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(
                signal.get().signalType(), is(RelativeStrengthSignal.SignalType.UNDERPERFORMING));
    }

    @Test
    void testSaveRsHistory() throws IOException {
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        relativeStrengthService.saveRsHistory();

        File rsFile = new File(rsDataFile);
        assertThat(rsFile.exists(), is(true));
        rsFile.delete();
    }

    @Test
    void testGetCurrentRsAndEma_withSufficientData() {
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        stubDailyPrices("SPY", constantPrices(60, 500.0));

        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("NVDA");

        assertThat(rsAndEma.isPresent(), is(true));
        assertThat(rsAndEma.get()[0], is(closeTo(1.2, 0.01)));
        assertThat(rsAndEma.get()[1], is(closeTo(1.2, 0.01)));
    }

    @Test
    void testGetCurrentRsAndEma_noData() {
        when(dailyPriceProvider.findDailyClosingPrices("UNKNOWN", RS_LOOKBACK_DAYS))
                .thenReturn(new ArrayList<>());

        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("UNKNOWN");

        assertThat(rsAndEma.isEmpty(), is(true));
    }

    @Test
    void testBenchmarkSymbol() {
        assertEquals("SPY", RelativeStrengthService.BENCHMARK_SYMBOL);
    }

    @Test
    void testLoadRsHistory_existingFile() throws IOException {
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");
        relativeStrengthService.saveRsHistory();

        File rsFile = new File(rsDataFile);
        assertThat(rsFile.exists(), is(true));

        RelativeStrengthService newService =
                new RelativeStrengthService(objectMapper, dailyPriceProvider);

        assertThat(newService.getRsHistory().containsKey("NVDA"), is(true));
        assertThat(newService.getRsHistory().get("NVDA").isInitialized(), is(true));
        rsFile.delete();
    }

    @Test
    void testLoadRsHistory_missingFile_isNoOp() throws IOException {
        new File(rsDataFile).delete();

        relativeStrengthService.loadRsHistory();

        assertThat(relativeStrengthService.getRsHistory().isEmpty(), is(true));
    }

    @Test
    void testLoadRsHistory_invalidFile_throws() throws Exception {
        File rsFile = new File(rsDataFile);
        rsFile.getParentFile().mkdirs();
        rsFile.createNewFile();

        ObjectMapper failingMapper = org.mockito.Mockito.spy(new ObjectMapper());
        doThrow(new IOException("bad rs data"))
                .when(failingMapper)
                .readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        assertThrows(
                IOException.class,
                () -> new RelativeStrengthService(failingMapper, dailyPriceProvider));

        rsFile.delete();
    }

    @Test
    void testSaveRsHistory_writeFailure_throws() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.spy(new ObjectMapper());
        doThrow(new IOException("write failed"))
                .when(failingMapper)
                .writeValue(any(File.class), any());

        RelativeStrengthService service =
                new RelativeStrengthService(failingMapper, dailyPriceProvider);

        assertThrows(IOException.class, service::saveRsHistory);
    }

    @Test
    void testGetCurrentRsAndEma_withInsufficientHistory() {
        stubDailyPrices("MSFT", constantPrices(5, 300.0));
        stubDailyPrices("SPY", constantPrices(5, 500.0));

        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("MSFT");

        assertThat(rsAndEma.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_noCrossover() {
        // First calculation to initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Manually set previous state where RS was above EMA and still is
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.25);
        rsData.setPreviousEma(1.2);

        // RS is still above EMA (no crossover)
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 610.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testRsCalculation_correctRatio() {
        stubDailyPrices("SPY", constantPrices(60, 400.0));
        stubDailyPrices("AAPL", constantPrices(60, 200.0));

        relativeStrengthService.calculateRelativeStrength("AAPL", "Apple");

        var rsData = relativeStrengthService.getRsHistory().get("AAPL");
        assertThat(rsData.getLatestRs(), is(closeTo(0.5, 0.001)));
    }

    @Test
    void testGetCurrentRsResult_withCompleteData() {
        stubDailyPrices("XLK", constantPrices(55, 600.0));
        stubDailyPrices("SPY", constantPrices(55, 500.0));

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().isComplete(), is(true));
        assertThat(result.get().dataPoints(), is(55));
        assertThat(result.get().rs(), is(closeTo(1.2, 0.01)));
    }

    @Test
    void testGetCurrentRsResult_withIncompleteData() {
        stubDailyPrices("XLK", constantPrices(30, 600.0));
        stubDailyPrices("SPY", constantPrices(30, 500.0));

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().isComplete(), is(false));
        assertThat(result.get().dataPoints(), is(30));
    }

    @Test
    void testGetCurrentRsResult_withNoSpyData() {
        stubDailyPrices("XLK", constantPrices(60, 600.0));
        when(dailyPriceProvider.findDailyClosingPrices("SPY", RS_LOOKBACK_DAYS))
                .thenReturn(new ArrayList<>());

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsResult_withMismatchedDates() {
        stubDailyPrices("XLK", constantPrices(60, 600.0));
        stubDailyPrices("SPY", constantPrices(60, 500.0));

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
    }

    @Test
    void testGetCurrentRsResult_returnsEmptyForBenchmark() {
        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("SPY");

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsResult_exactlyMinimumData() {
        stubDailyPrices("XLK", constantPrices(20, 600.0));
        stubDailyPrices("SPY", constantPrices(20, 500.0));

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().dataPoints(), is(20));
        assertThat(result.get().isComplete(), is(false));
    }

    @Test
    void testGetCurrentRsResult_exactlyFullData() {
        stubDailyPrices("XLK", constantPrices(50, 600.0));
        stubDailyPrices("SPY", constantPrices(50, 500.0));

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().dataPoints(), is(50));
        assertThat(result.get().isComplete(), is(true));
    }

    @Test
    void testRsResultRecord_isCompleteMethod() {
        RelativeStrengthService.RsResult complete =
                new RelativeStrengthService.RsResult(1.2, 1.1, 50, true);
        RelativeStrengthService.RsResult incomplete =
                new RelativeStrengthService.RsResult(1.2, 1.1, 30, false);

        assertThat(complete.isComplete(), is(true));
        assertThat(incomplete.isComplete(), is(false));
    }

    @Test
    void testRsResultRecord_fields() {
        RelativeStrengthService.RsResult result =
                new RelativeStrengthService.RsResult(1.25, 1.20, 45, false);

        assertThat(result.rs(), is(1.25));
        assertThat(result.ema(), is(1.20));
        assertThat(result.dataPoints(), is(45));
        assertThat(result.isComplete(), is(false));
    }

    @Test
    void testCrossover_belowDeadZoneToAboveDeadZone_signalGenerated() {
        // Initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 400.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Previous: RS meaningfully below EMA (> 0.2% below)
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(0.80);
        rsData.setPreviousEma(0.85); // pctDiff = ((0.80 - 0.85)/0.85)*100 = -5.88%

        // Current: RS meaningfully above EMA (> 0.2% above)
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 500.0, 700.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(signal.get().signalType(), is(RelativeStrengthSignal.SignalType.OUTPERFORMING));
    }

    @Test
    void testCrossover_aboveDeadZoneToBelowDeadZone_signalGenerated() {
        // Initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Previous: RS meaningfully above EMA (> 0.2% above)
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.30);
        rsData.setPreviousEma(1.20); // pctDiff = ((1.30 - 1.20)/1.20)*100 = +8.33%

        // Current: RS meaningfully below EMA (> 0.2% below)
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 700.0, 400.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(
                signal.get().signalType(), is(RelativeStrengthSignal.SignalType.UNDERPERFORMING));
    }

    @Test
    void testCrossover_withinDeadZone_noSignal() {
        // Initialize with constant prices: RS = 600/500 = 1.2, EMA converges to 1.2
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Previous: RS slightly below EMA, but within dead zone (< 0.2%)
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.199);
        rsData.setPreviousEma(1.200); // pctDiff = ((1.199-1.200)/1.200)*100 = -0.083%

        // Current: constant prices → RS ≈ EMA, pctDiff ≈ 0% (within dead zone)
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 601.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(
                "Should not trigger signal when both values are within dead zone",
                signal.isEmpty(),
                is(true));
    }

    @Test
    void testCrossover_previousInsideDeadZoneCurrentOutside_noSignal() {
        // Initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Previous: RS slightly below EMA but inside dead zone
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.199);
        rsData.setPreviousEma(1.200); // pctDiff = -0.083%, inside ±0.2%

        // Current: RS meaningfully above EMA (outside dead zone)
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 500.0, 700.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(
                "Should not trigger when previous RS is inside dead zone",
                signal.isEmpty(),
                is(true));
    }

    @Test
    void testCrossover_deadZoneBoundary_noSignal() {
        // Initialize
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", constantPrices(60, 600.0));
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Previous: RS exactly at dead zone boundary below EMA
        // pctDiff = exactly -RS_EMA_DEAD_ZONE = -0.2%
        // prevRs = prevEma * (1 - 0.002) = 1.200 * 0.998 = 1.1976
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        double ema = 1.200;
        rsData.setPreviousRs(ema * (1.0 - RelativeStrengthService.RS_EMA_DEAD_ZONE / 100.0));
        rsData.setPreviousEma(ema);

        // Current: RS meaningfully above EMA
        stubDailyPrices("SPY", constantPrices(60, 500.0));
        stubDailyPrices("NVDA", risingPrices(60, 500.0, 700.0));

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(
                "Exact dead zone boundary should not trigger signal",
                signal.isEmpty(),
                is(true));
    }

    // --- helpers ---

    private void stubDailyPrices(String symbol, List<DailyPrice> prices) {
        when(dailyPriceProvider.findDailyClosingPrices(symbol, RS_LOOKBACK_DAYS))
                .thenReturn(prices);
    }

    private List<DailyPrice> constantPrices(int count, double price) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(new DailyPrice(LocalDate.now().minusDays(count - 1 - i), price));
        }
        return prices;
    }

    private List<DailyPrice> risingPrices(int count, double startPrice, double endPrice) {
        List<DailyPrice> prices = new ArrayList<>();
        double step = (endPrice - startPrice) / (count - 1);
        for (int i = 0; i < count; i++) {
            prices.add(
                    new DailyPrice(
                            LocalDate.now().minusDays(count - 1 - i), startPrice + step * i));
        }
        return prices;
    }
}
