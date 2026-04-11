package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.service.model.RsiDailyClosePrice;

@SpringBootTest
class RsiServiceTest {

    @MockitoBean private TelegramGateway telegramClient;
    @MockitoBean private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @MockitoBean private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @Autowired private ObjectMapper objectMapper;

    private RsiService rsiService;

    private final StockSymbol symbol = new StockSymbol("AAPL", "Apple");
    private final String rsiDataFile = "config/rsi-data.json";

    @BeforeEach
    void setUp() throws IOException {
        new File(rsiDataFile).delete();
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        rsiService =
                spy(
                        new RsiService(
                                telegramClient,
                                objectMapper,
                                finnhubPriceEvaluator,
                                coinGeckoPriceEvaluator));
    }

    @Test
    void testAddPrice_onlyStoresData_doesNotCalculateRsi() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        // addPrice should NOT send any messages or calculate RSI
        verify(telegramClient, never()).sendMessage(anyString());
        verify(telegramClient, never()).sendMessageAndReturnId(anyString());
        // Price data should be stored
        assertEquals(15, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
        verify(rsiService, times(15)).savePriceHistory();
    }

    @Test
    void testAddPrice_storesDisplayName_forStock() throws IOException {
        rsiService.addPrice(symbol, 100, 0.0, LocalDate.now());

        assertEquals("Apple (AAPL)", rsiService.getSymbolDisplayNames().get("AAPL"));
    }

    @Test
    void testAddPrice_storesDisplayName_forCrypto() throws IOException {
        CoinId cryptoSymbol = CoinId.BITCOIN;
        rsiService.addPrice(cryptoSymbol, 50000, 0.0, LocalDate.now());

        assertEquals(
                cryptoSymbol.getName(),
                rsiService.getSymbolDisplayNames().get(cryptoSymbol.getName()));
    }

    @Test
    void testAnalyzeAllSymbols_detectsOverbought() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(signals.stream().anyMatch(s -> "OVERBOUGHT".equals(s.zone())), is(true));
        assertThat(
                signals.stream().anyMatch(s -> "Apple (AAPL)".equals(s.displayName())), is(true));
    }

    @Test
    void testAnalyzeAllSymbols_detectsOversold() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), 0.0, LocalDate.now().minusDays(14 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(signals.stream().anyMatch(s -> "OVERSOLD".equals(s.zone())), is(true));
    }

    @Test
    void testAnalyzeAllSymbols_insufficientData_noSignals() throws IOException {
        for (int i = 0; i < 10; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(9 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(empty()));
    }

    @Test
    void testAnalyzeAllSymbols_neutralRsi_noSignals() throws IOException {
        // Mixed prices that should produce neutral RSI (between 30 and 70)
        double[] mixedPrices = {
            100, 105, 102, 108, 104, 110, 106, 112, 108, 114, 110, 116, 112, 118, 114
        };
        for (int i = 0; i < mixedPrices.length; i++) {
            rsiService.addPrice(symbol, mixedPrices[i], 0.0, LocalDate.now().minusDays(14 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(empty()));
    }

    @Test
    void testAnalyzeAllSymbols_usesDisplayNameFromMap() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        // StockSymbol("AAPL", "Apple") display name should be "Apple (AAPL)"
        assertThat(signals.getFirst().displayName(), is("Apple (AAPL)"));
    }

    @Test
    void testAnalyzeAllSymbols_fallsBackToSymbolKey_whenNoDisplayName() {
        // Manually add price data without going through addPrice (no display name registered)
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        for (int i = 0; i < 15; i++) {
            priceData.addPrice(LocalDate.now().minusDays(14 - i), 100 + i);
        }
        rsiService.getPriceHistory().put("UNKNOWN", priceData);

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(signals.getFirst().displayName(), is("UNKNOWN"));
    }

    @Test
    void testSendRsiReport_sendsConsolidatedReport() throws IOException {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        rsiService.sendRsiReport();

        verify(telegramClient, times(1)).sendMessageAndReturnId(contains("RSI Signal Report"));
    }

    @Test
    void testSendRsiReport_noSignals_doesNotSend() {
        rsiService.sendRsiReport();

        verify(telegramClient, never()).sendMessageAndReturnId(anyString());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void testSendRsiReport_deletesPreviousReport() throws IOException {
        when(telegramClient.sendMessageAndReturnId(anyString()))
                .thenReturn(OptionalLong.of(100L))
                .thenReturn(OptionalLong.of(200L));

        // First report
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }
        rsiService.sendRsiReport();

        // Add more data for second report (prices keep going up => stays overbought)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(
                    new StockSymbol("MSFT", "Microsoft"),
                    100 + i,
                    0.0,
                    LocalDate.now().minusDays(14 - i));
        }
        rsiService.sendRsiReport();

        verify(telegramClient, times(1)).deleteMessage(100L);
    }

    @Test
    void testSendRsiReport_noDeleteWhenNoPreviousMessage() throws IOException {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(100L));

        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }
        rsiService.sendRsiReport();

        verify(telegramClient, never()).deleteMessage(anyLong());
    }

    @Test
    void testBuildRsiReport_overboughtSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal("Apple", 75.5, 72.0, 3.5, "OVERBOUGHT"),
                        new RsiService.RsiSignal("Microsoft", 80.2, 78.0, 2.2, "OVERBOUGHT"));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("*RSI Signal Report*"));
        assertThat(report, containsString("🔴 *Overbought (RSI ≥ 70):*"));
        assertThat(report, containsString("Apple"));
        assertThat(report, containsString("Microsoft"));
        assertThat(report, containsString("2 signal(s): 2 overbought, 0 oversold"));
    }

    @Test
    void testBuildRsiReport_oversoldSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(new RsiService.RsiSignal("Tesla", 25.3, 28.0, -2.7, "OVERSOLD"));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("🟢 *Oversold (RSI ≤ 30):*"));
        assertThat(report, containsString("Tesla"));
        assertThat(report, containsString("1 signal(s): 0 overbought, 1 oversold"));
    }

    @Test
    void testBuildRsiReport_mixedSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal("Apple", 75.5, 72.0, 3.5, "OVERBOUGHT"),
                        new RsiService.RsiSignal("Tesla", 25.3, 28.0, -2.7, "OVERSOLD"));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("🔴 *Overbought (RSI ≥ 70):*"));
        assertThat(report, containsString("🟢 *Oversold (RSI ≤ 30):*"));
        assertThat(report, containsString("2 signal(s): 1 overbought, 1 oversold"));
    }

    @Test
    void testBuildRsiReport_withNoPreviousRsi() {
        List<RsiService.RsiSignal> signals =
                List.of(new RsiService.RsiSignal("Apple", 75.5, 0, 75.5, "OVERBOUGHT"));

        String report = rsiService.buildRsiReport(signals);

        // Should not include diff when previousRsi is 0
        assertThat(report, containsString("Apple: 75.50"));
        assertThat(report, not(containsString("(+")));
    }

    @Test
    void testBuildRsiReport_withRsiDiff() {
        List<RsiService.RsiSignal> signals =
                List.of(new RsiService.RsiSignal("Apple", 75.5, 72.0, 3.5, "OVERBOUGHT"));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("Apple: 75.50 (+3.5)"));
    }

    @Test
    void testCalculateRsi_withLosses() {
        java.util.List<Double> decliningPrices =
                java.util.Arrays.asList(
                        100.0, 95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 60.0, 55.0, 50.0, 45.0,
                        40.0, 35.0, 30.0);

        double rsi = rsiService.calculateRsi(decliningPrices);

        assertThat(rsi, is(closeTo(0.0, 0.1)));
    }

    @Test
    void testCalculateRsi_mixedPrices() {
        java.util.List<Double> mixedPrices =
                java.util.Arrays.asList(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0, 118.0, 114.0);

        double rsi = rsiService.calculateRsi(mixedPrices);

        assertThat(rsi, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testCalculateRsi_insufficientPrices() {
        java.util.List<Double> insufficientPrices =
                java.util.Arrays.asList(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0);

        double rsi = rsiService.calculateRsi(insufficientPrices);
        assertEquals(50, rsi);
    }

    @Test
    void testPriceHistoryPersistence() throws Exception {
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        Map<String, RsiDailyClosePrice> expectedHistory = new HashMap<>();
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.now(), 150.0);
        expectedHistory.put(symbol.getName(), priceData);

        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doReturn(expectedHistory)
                .when(spyObjectMapper)
                .readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        RsiService serviceWithHistory =
                new RsiService(
                        telegramClient,
                        spyObjectMapper,
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator);

        assertEquals(
                1, serviceWithHistory.getPriceHistory().get(symbol.getName()).getPrices().size());
        assertEquals(
                150.0,
                serviceWithHistory
                        .getPriceHistory()
                        .get(symbol.getName())
                        .getPriceValues()
                        .getFirst());

        dummyFile.delete();
    }

    @Test
    void testLoadPriceHistory_fileNotFound() throws IOException {
        new File(rsiDataFile).delete();

        rsiService.loadPriceHistory();

        assertEquals(0, rsiService.getPriceHistory().size());
    }

    @Test
    void testLoadPriceHistory_invalidFile() throws Exception {
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doThrow(new java.io.IOException("Invalid file format"))
                .when(spyObjectMapper)
                .readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        assertThrows(
                IOException.class,
                () ->
                        new RsiService(
                                telegramClient,
                                spyObjectMapper,
                                finnhubPriceEvaluator,
                                coinGeckoPriceEvaluator));

        dummyFile.delete();
    }

    @Test
    void testObjectMapperCanSerializeLocalDate() throws Exception {
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.of(2023, 1, 15), 150.0);

        Map<String, RsiDailyClosePrice> testData = new HashMap<>();
        testData.put(symbol.getName(), priceData);

        String json = objectMapper.writeValueAsString(testData);
        assertThat(json, containsString("[2023,1,15]"));

        Map<String, RsiDailyClosePrice> deserializedData =
                objectMapper.readValue(
                        json,
                        objectMapper
                                .getTypeFactory()
                                .constructMapType(
                                        HashMap.class, String.class, RsiDailyClosePrice.class));

        assertEquals(1, deserializedData.get(symbol.getName()).getPrices().size());
        assertEquals(
                LocalDate.of(2023, 1, 15),
                deserializedData.get(symbol.getName()).getPrices().getFirst().getDate());
        assertEquals(
                150.0, deserializedData.get(symbol.getName()).getPrices().getFirst().getPrice());
    }

    @Test
    void testHolidayDetection_skipsWhenMarketHolidayDetected() throws IOException {
        // Holiday scenario: currentPrice == previousClose (Finnhub returns last close as current)
        when(finnhubPriceEvaluator.isPotentialMarketHoliday("AAPL", 150.50, 150.50))
                .thenReturn(true);

        rsiService.addPrice(symbol, 150.50, 150.50, LocalDate.now());

        assertThat(rsiService.getPriceHistory().containsKey(symbol.getName()), is(false));
    }

    @Test
    void testHolidayDetection_addsWhenNotMarketHoliday() throws IOException {
        // Normal trading day: currentPrice != previousClose
        when(finnhubPriceEvaluator.isPotentialMarketHoliday("AAPL", 152.30, 150.50))
                .thenReturn(false);

        rsiService.addPrice(symbol, 152.30, 150.50, LocalDate.now());

        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testHolidayDetection_delegatesToFinnhubPriceEvaluator() throws IOException {
        rsiService.addPrice(symbol, 152.30, 150.50, LocalDate.now());

        verify(finnhubPriceEvaluator).isPotentialMarketHoliday("AAPL", 152.30, 150.50);
    }

    @Test
    void testHolidayDetection_previousClosePassedCorrectly() throws IOException {
        // Verify that previousClose is forwarded to the evaluator, not hardcoded to 0.0
        double currentPrice = 175.25;
        double previousClose = 173.80;
        rsiService.addPrice(symbol, currentPrice, previousClose, LocalDate.now());

        verify(finnhubPriceEvaluator).isPotentialMarketHoliday("AAPL", currentPrice, previousClose);
    }

    @Test
    void testHolidayDetection_cryptoSkipsHolidayCheck() throws IOException {
        // Crypto uses 0.0 for previousClose; holiday detection should not trigger
        CoinId cryptoSymbol = CoinId.BITCOIN;
        when(finnhubPriceEvaluator.isPotentialMarketHoliday(anyString(), anyDouble(), anyDouble()))
                .thenReturn(false);

        rsiService.addPrice(cryptoSymbol, 50000.0, 0.0, LocalDate.now());

        assertEquals(
                1, rsiService.getPriceHistory().get(cryptoSymbol.getName()).getPrices().size());
    }

    @Test
    void testAnalyzeAllSymbols_rsiDiffCalculation() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), 0.0, LocalDate.now().minusDays(15 - i));
        }
        // Set a known previousRsi so the diff is meaningful
        rsiService.getPriceHistory().get(symbol.getName()).setPreviousRsi(10);

        // Add one more price to keep it in oversold territory
        rsiService.addPrice(symbol, 120, 0.0, LocalDate.now());

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        RsiService.RsiSignal signal = signals.getFirst();
        assertThat(signal.previousRsi(), is(closeTo(10.0, 0.01)));
        assertThat(signal.rsiDiff(), is(not(closeTo(0.0, 0.01))));
    }

    @Test
    void testGetCurrentRsi_withSufficientData() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_withInsufficientData() throws IOException {
        for (int i = 0; i < 10; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(9 - i));
        }

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsi_noData() {
        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void testAnalyzeAllSymbols_cryptoDisplayName() throws IOException {
        CoinId cryptoSymbol = CoinId.BITCOIN;

        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(cryptoSymbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(
                signals.stream().anyMatch(s -> s.displayName().equals(cryptoSymbol.getName())),
                is(true));
    }

    @Test
    void testSavePriceHistory_ioException() throws IOException {
        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doThrow(new IOException("Write error"))
                .when(spyObjectMapper)
                .writeValue(any(File.class), any());

        RsiService serviceWithFailingMapper =
                new RsiService(
                        telegramClient,
                        spyObjectMapper,
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator);

        assertThrows(
                IOException.class,
                () -> serviceWithFailingMapper.addPrice(symbol, 100.0, 0.0, LocalDate.now()));
    }

    @Test
    void testCalculateRsi_averageLossZero() {
        java.util.List<Double> allGainsAfterInitial =
                java.util.Arrays.asList(
                        100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0, 108.0, 109.0, 110.0,
                        111.0, 112.0, 113.0, 114.0);

        double rsi = rsiService.calculateRsi(allGainsAfterInitial);

        assertEquals(100.0, rsi, 0.001);
    }

    @Test
    void testGetCurrentPriceFromCache_stockSymbol() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 150.0));

        Double price = rsiService.getCurrentPriceFromCache(new StockSymbol("AAPL", "Apple"));

        assertEquals(150.0, price);
    }

    @Test
    void testGetCurrentPriceFromCache_cryptoSymbol() {
        when(coinGeckoPriceEvaluator.getLastPriceCache())
                .thenReturn(Map.of(CoinId.BITCOIN, 50000.0));

        Double price = rsiService.getCurrentPriceFromCache(CoinId.BITCOIN);

        assertEquals(50000.0, price);
    }

    @Test
    void testGetCurrentPriceFromCache_nullCaches() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());

        Double stockPrice = rsiService.getCurrentPriceFromCache(new StockSymbol("AAPL", "Apple"));
        Double cryptoPrice = rsiService.getCurrentPriceFromCache(CoinId.BITCOIN);

        assertThat(stockPrice, is(nullValue()));
        assertThat(cryptoPrice, is(nullValue()));
    }

    @Test
    void testGetCurrentRsi_withCurrentPriceFromCache() throws IOException {
        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(13 - i));
        }

        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 125.0));

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_withCurrentPriceFromCache_crypto() throws IOException {
        CoinId cryptoSymbol = CoinId.BITCOIN;

        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(
                    cryptoSymbol, 40000 + (i * 1000), 0.0, LocalDate.now().minusDays(13 - i));
        }

        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of(cryptoSymbol, 55000.0));

        var rsi = rsiService.getCurrentRsi(cryptoSymbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_insufficientDataEvenWithCache() throws IOException {
        for (int i = 0; i < 10; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(9 - i));
        }

        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 125.0));

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void testAnalyzeAllSymbols_updatesPreviousRsi() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }

        double previousRsiBefore =
                rsiService.getPriceHistory().get(symbol.getName()).getPreviousRsi();
        rsiService.analyzeAllSymbols();
        double previousRsiAfter =
                rsiService.getPriceHistory().get(symbol.getName()).getPreviousRsi();

        // After analysis, previousRsi should be updated from its initial value
        assertThat(previousRsiAfter, is(not(closeTo(previousRsiBefore, 0.01))));
    }

    @Test
    void testGetCurrentPriceFromCacheByKey_stock() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 150.0));

        Double price = rsiService.getCurrentPriceFromCacheByKey("AAPL");

        assertEquals(150.0, price);
    }

    @Test
    void testGetCurrentPriceFromCacheByKey_crypto() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        when(coinGeckoPriceEvaluator.getLastPriceCache())
                .thenReturn(Map.of(CoinId.BITCOIN, 50000.0));

        Double price = rsiService.getCurrentPriceFromCacheByKey("bitcoin");

        assertEquals(50000.0, price);
    }

    @Test
    void testGetCurrentPriceFromCacheByKey_notFound() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());

        Double price = rsiService.getCurrentPriceFromCacheByKey("UNKNOWN");

        assertThat(price, is(nullValue()));
    }

    @Test
    void testAnalyzeAllSymbols_usesCurrentPriceFromCache() throws IOException {
        // Add only 14 historical prices (not enough alone for RSI)
        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(13 - i));
        }

        // Provide a current price via the cache to make 15 total
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 120.0));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        // With 14 historical + 1 cache = 15 prices, RSI should be calculable
        assertThat(signals, is(not(empty())));
    }

    @Test
    void testSendRsiReport_analyzesAndSendsInOneStep() throws IOException {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        // Add overbought data
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, 0.0, LocalDate.now().minusDays(14 - i));
        }
        // Add oversold data for another symbol
        StockSymbol symbol2 = new StockSymbol("TSLA", "Tesla");
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol2, 200 - (i * 5), 0.0, LocalDate.now().minusDays(14 - i));
        }

        rsiService.sendRsiReport();

        verify(telegramClient, times(1))
                .sendMessageAndReturnId(
                        argThat(
                                report ->
                                        report.contains("RSI Signal Report")
                                                && report.contains("Overbought")
                                                && report.contains("Oversold")));
    }

    @Test
    void testRemoveSymbolRsiData_success() throws IOException {
        rsiService.addPrice(symbol, 100.0, LocalDate.now());

        boolean removed = rsiService.removeSymbolRsiData("AAPL");

        assertThat(removed, is(true));
        assertThat(rsiService.getPriceHistory().containsKey("AAPL"), is(false));
    }

    @Test
    void testRemoveSymbolRsiData_unknownSymbol() {
        boolean removed = rsiService.removeSymbolRsiData("UNKNOWN");

        assertThat(removed, is(false));
    }

    @Test
    void testRemoveSymbolRsiData_blankOrNullSymbol() {
        assertThat(rsiService.removeSymbolRsiData(""), is(false));
        assertThat(rsiService.removeSymbolRsiData(null), is(false));
    }

    @Test
    void testRemoveSymbolRsiData_returnsFalseWhenSaveFails() throws Exception {
        rsiService.addPrice(symbol, 100.0, LocalDate.now());
        doThrow(new IOException("save failed")).when(rsiService).savePriceHistory();

        boolean removed = rsiService.removeSymbolRsiData("AAPL");

        assertThat(removed, is(false));
        assertThat(rsiService.getPriceHistory().containsKey("AAPL"), is(false));
    }
}
