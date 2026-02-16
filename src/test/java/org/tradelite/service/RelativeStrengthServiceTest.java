package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.core.RelativeStrengthSignal;
import org.tradelite.service.model.RsiDailyClosePrice;

@SpringBootTest
class RelativeStrengthServiceTest {

    @MockitoBean private TelegramClient telegramClient;
    @MockitoBean private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @MockitoBean private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @Autowired private ObjectMapper objectMapper;

    private RsiService rsiService;
    private RelativeStrengthService relativeStrengthService;

    private final String rsDataFile = "config/rs-data.json";

    @BeforeEach
    void setUp() throws IOException {
        new File(rsDataFile).delete();
        String rsiDataFile = "config/rsi-data.json";
        new File(rsiDataFile).delete();
        rsiService =
                new RsiService(
                        telegramClient,
                        objectMapper,
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator);
        relativeStrengthService = new RelativeStrengthService(objectMapper, rsiService);
    }

    @Test
    void testCalculateEma_withSufficientData() {
        // Test EMA calculation with 50+ values
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            values.add(1.0 + (i * 0.01)); // Gradually increasing RS values
        }

        double ema = relativeStrengthService.calculateEma(values, 50);

        assertThat(ema, is(greaterThan(1.0)));
        assertThat(ema, is(lessThan(2.0)));
    }

    @Test
    void testCalculateEma_withExactPeriodData() {
        // Test EMA with exactly 50 values (should use SMA only)
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            values.add(1.0); // Constant values
        }

        double ema = relativeStrengthService.calculateEma(values, 50);

        // EMA of constant values should equal the constant
        assertThat(ema, is(closeTo(1.0, 0.001)));
    }

    @Test
    void testCalculateEma_withInsufficientData() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            values.add(1.0);
        }

        double ema = relativeStrengthService.calculateEma(values, 50);

        assertEquals(0, ema);
    }

    @Test
    void testCalculateRelativeStrength_skipsBenchmark() {
        // SPY should be skipped
        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength(
                        RelativeStrengthService.BENCHMARK_SYMBOL, "SPDR S&P 500 ETF");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientStockData() {
        // No price data added
        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientSpyData() {
        // Add stock data but no SPY data
        for (int i = 0; i < 60; i++) {
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(LocalDate.now().minusDays(59 - i), 500.0 + i);
        }

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_insufficientHistory() {
        // Add only 30 days of data (less than 50 required for EMA)
        for (int i = 0; i < 30; i++) {
            LocalDate date = LocalDate.now().minusDays(29 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 600.0);
        }

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_noSignalOnFirstCalculation() {
        // Add 60 days of aligned price data
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 600.0);
        }

        // First calculation should not produce a signal (no previous state)
        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isEmpty(), is(true));

        // Verify RS data was stored
        assertThat(relativeStrengthService.getRsHistory().containsKey("NVDA"), is(true));
        assertThat(relativeStrengthService.getRsHistory().get("NVDA").isInitialized(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_crossoverUp() {
        // Set up initial state where RS is below EMA
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(60 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            // Stock starts weak relative to SPY
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 400.0 + (i < 55 ? 0 : i * 5));
        }

        // First calculation to initialize
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Manually set previous state to simulate RS being below EMA
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(0.8); // Below EMA
        rsData.setPreviousEma(0.85); // EMA higher than RS

        // Add new data point where RS jumps above EMA
        LocalDate today = LocalDate.now();
        rsiService.getPriceHistory().get("SPY").addPrice(today, 500.0);
        rsiService.getPriceHistory().get("NVDA").addPrice(today, 500.0); // RS = 1.0, above EMA

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(signal.get().signalType(), is(RelativeStrengthSignal.SignalType.OUTPERFORMING));
        assertThat(signal.get().symbol(), is("NVDA"));
        assertThat(signal.get().displayName(), is("Nvidia"));
    }

    @Test
    void testCalculateRelativeStrength_crossoverDown() {
        // Set up initial state
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(60 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 600.0);
        }

        // First calculation to initialize
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Manually set previous state to simulate RS being above EMA
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.3); // Above EMA
        rsData.setPreviousEma(1.2); // EMA lower than RS

        // Add new data point where RS drops below EMA
        LocalDate today = LocalDate.now();
        rsiService.getPriceHistory().get("SPY").addPrice(today, 500.0);
        rsiService.getPriceHistory().get("NVDA").addPrice(today, 500.0); // RS drops to 1.0

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        assertThat(signal.isPresent(), is(true));
        assertThat(
                signal.get().signalType(), is(RelativeStrengthSignal.SignalType.UNDERPERFORMING));
    }

    @Test
    void testSaveRsHistory() throws IOException {
        // Add data and calculate RS
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 600.0);
        }
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // Save
        relativeStrengthService.saveRsHistory();

        // Verify file exists
        File rsFile = new File(rsDataFile);
        assertThat(rsFile.exists(), is(true));

        // Cleanup
        rsFile.delete();
    }

    @Test
    void testGetCurrentRsAndEma_withSufficientData() {
        // Add 60 days of aligned price data
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 500.0);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("NVDA", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 600.0);
        }

        // Calculate RS to populate history
        relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("NVDA");

        assertThat(rsAndEma.isPresent(), is(true));
        assertThat(rsAndEma.get()[0], is(closeTo(1.2, 0.01))); // RS = 600/500 = 1.2
        assertThat(rsAndEma.get()[1], is(closeTo(1.2, 0.01))); // EMA of constant RS
    }

    @Test
    void testGetCurrentRsAndEma_noData() {
        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("UNKNOWN");

        assertThat(rsAndEma.isEmpty(), is(true));
    }

    @Test
    void testBenchmarkSymbol() {
        assertEquals("SPY", RelativeStrengthService.BENCHMARK_SYMBOL);
    }

    @Test
    void testRsCalculation_correctRatio() {
        // Add aligned price data
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("SPY", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 400.0); // SPY at 400
            rsiService
                    .getPriceHistory()
                    .computeIfAbsent("AAPL", _ -> new RsiDailyClosePrice())
                    .addPrice(date, 200.0); // AAPL at 200
        }

        relativeStrengthService.calculateRelativeStrength("AAPL", "Apple");

        // RS should be 200/400 = 0.5
        var rsData = relativeStrengthService.getRsHistory().get("AAPL");
        assertThat(rsData.getLatestRs(), is(closeTo(0.5, 0.001)));
    }
}
