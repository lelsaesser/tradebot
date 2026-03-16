package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

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
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.RsiDailyClosePrice;

@SpringBootTest
class RelativeStrengthServiceTest {

    @MockitoBean private TelegramClient telegramClient;
    @MockitoBean private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @MockitoBean private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;
    @MockitoBean private PriceQuoteRepository priceQuoteRepository;

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
        relativeStrengthService =
                new RelativeStrengthService(objectMapper, rsiService, priceQuoteRepository);
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
        // Mock the repository to return 60 days of daily price data
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("NVDA", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("NVDA");

        assertThat(rsAndEma.isPresent(), is(true));
        assertThat(rsAndEma.get()[0], is(closeTo(1.2, 0.01))); // RS = 600/500 = 1.2
        assertThat(rsAndEma.get()[1], is(closeTo(1.2, 0.01))); // EMA of constant RS
    }

    @Test
    void testGetCurrentRsAndEma_noData() {
        // Mock repository to return empty data
        when(priceQuoteRepository.findDailyClosingPrices("UNKNOWN", 80))
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
        // First, create and save RS data
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
        relativeStrengthService.saveRsHistory();

        // Verify file exists
        File rsFile = new File(rsDataFile);
        assertThat(rsFile.exists(), is(true));

        // Create a new service that should load from the file
        RelativeStrengthService newService =
                new RelativeStrengthService(objectMapper, rsiService, priceQuoteRepository);

        // Verify data was loaded
        assertThat(newService.getRsHistory().containsKey("NVDA"), is(true));
        assertThat(newService.getRsHistory().get("NVDA").isInitialized(), is(true));

        // Cleanup
        rsFile.delete();
    }

    @Test
    void testGetCurrentRsAndEma_withInsufficientHistory() {
        // Mock repository to return only 5 days of data (less than 10 minimum)
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate date = LocalDate.now().minusDays(4 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(300.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("MSFT", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        // Should return empty because insufficient history (less than 10 minimum)
        Optional<double[]> rsAndEma = relativeStrengthService.getCurrentRsAndEma("MSFT");

        assertThat(rsAndEma.isEmpty(), is(true));
    }

    @Test
    void testCalculateRelativeStrength_noCrossover() {
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

        // Manually set previous state where RS was above EMA and still is
        var rsData = relativeStrengthService.getRsHistory().get("NVDA");
        rsData.setPreviousRs(1.25); // Above EMA
        rsData.setPreviousEma(1.2); // EMA lower than RS

        // Add new data point where RS is still above EMA (no crossover)
        LocalDate today = LocalDate.now();
        rsiService.getPriceHistory().get("SPY").addPrice(today, 500.0);
        rsiService.getPriceHistory().get("NVDA").addPrice(today, 610.0); // RS = 1.22, still above

        Optional<RelativeStrengthSignal> signal =
                relativeStrengthService.calculateRelativeStrength("NVDA", "Nvidia");

        // No crossover because RS stayed above EMA
        assertThat(signal.isEmpty(), is(true));
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

    @Test
    void testGetCurrentRsResult_withCompleteData() {
        // Mock repository to return 50+ days of data (complete)
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            LocalDate date = LocalDate.now().minusDays(54 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().isComplete(), is(true));
        assertThat(result.get().dataPoints(), is(55));
        assertThat(result.get().rs(), is(closeTo(1.2, 0.01)));
    }

    @Test
    void testGetCurrentRsResult_withIncompleteData() {
        // Mock repository to return 30 days of data (incomplete but above minimum)
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate date = LocalDate.now().minusDays(29 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().isComplete(), is(false)); // Less than 50 data points
        assertThat(result.get().dataPoints(), is(30));
    }

    @Test
    void testGetCurrentRsResult_withNoSpyData() {
        // Mock repository to return data for stock but not SPY
        List<DailyPrice> stockPrices = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(new ArrayList<>());

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsResult_withMismatchedDates() {
        // Mock repository to return data with different dates for stock and SPY
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();

        // Stock has data from last 60 days
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);
        }

        // SPY has data from last 60 days (same dates)
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.now().minusDays(59 - i);
            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }

        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
    }

    @Test
    void testGetCurrentRsResult_returnsEmptyForBenchmark() {
        // Requesting RS for SPY itself should return empty
        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("SPY");

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsResult_exactlyMinimumData() {
        // Mock repository to return exactly 20 days (minimum required)
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            LocalDate date = LocalDate.now().minusDays(19 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

        Optional<RelativeStrengthService.RsResult> result =
                relativeStrengthService.getCurrentRsResult("XLK");

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().dataPoints(), is(20));
        assertThat(result.get().isComplete(), is(false));
    }

    @Test
    void testGetCurrentRsResult_exactlyFullData() {
        // Mock repository to return exactly 50 days (full EMA period)
        List<DailyPrice> stockPrices = new ArrayList<>();
        List<DailyPrice> spyPrices = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            LocalDate date = LocalDate.now().minusDays(49 - i);
            DailyPrice stockPrice = new DailyPrice();
            stockPrice.setDate(date);
            stockPrice.setPrice(600.0);
            stockPrices.add(stockPrice);

            DailyPrice spyPrice = new DailyPrice();
            spyPrice.setDate(date);
            spyPrice.setPrice(500.0);
            spyPrices.add(spyPrice);
        }
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 80)).thenReturn(stockPrices);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 80)).thenReturn(spyPrices);

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
}
