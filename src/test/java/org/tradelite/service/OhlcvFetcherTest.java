package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.twelvedata.TwelveDataClient;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.repository.OhlcvRepository;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class OhlcvFetcherTest {

    @Mock private TwelveDataClient twelveDataClient;
    @Mock private OhlcvRepository ohlcvRepository;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private TelegramGateway telegramGateway;

    private StockSplitDetector stockSplitDetector;
    private OhlcvFetcher ohlcvFetcher;

    @BeforeEach
    void setUp() {
        stockSplitDetector = new StockSplitDetector();
        ohlcvFetcher =
                new OhlcvFetcher(
                        twelveDataClient,
                        ohlcvRepository,
                        symbolRegistry,
                        telegramGateway,
                        stockSplitDetector);
        ohlcvFetcher.setRequestDelayMs(0);
        // Default: return all ETFs + benchmark (no extra stocks)
        lenient().when(symbolRegistry.getAll()).thenReturn(defaultEtfSymbols());
        // Default: all symbols need backfill
        lenient().when(ohlcvRepository.findBySymbol(anyString(), anyInt())).thenReturn(List.of());
        // Default: fetch returns empty
        lenient()
                .when(twelveDataClient.fetchDailyOhlcv(anyString(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    void fetchAndBackfillOhlcv_emptyTable_triggersBackfill() throws InterruptedException {
        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("SPY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_sufficientData_triggersRefresh() throws InterruptedException {
        List<OhlcvRecord> sufficientRecords = generateRecords("SPY", 140);
        when(ohlcvRepository.findBySymbol("SPY", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(sufficientRecords);

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("SPY", OhlcvFetcher.REFRESH_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_includesEtfsFromRegistry() throws InterruptedException {
        ohlcvFetcher.fetchAndBackfillOhlcv();

        for (String etf : SymbolRegistry.BROAD_SECTOR_ETFS.keySet()) {
            verify(twelveDataClient).fetchDailyOhlcv(eq(etf), anyInt());
        }
        verify(twelveDataClient).fetchDailyOhlcv(eq(SymbolRegistry.BENCHMARK_SYMBOL), anyInt());
    }

    @Test
    void fetchAndBackfillOhlcv_includesNonEtfStocks() throws InterruptedException {
        List<StockSymbol> symbols = new ArrayList<>(defaultEtfSymbols());
        symbols.add(new StockSymbol("AAPL", "Apple Inc"));
        when(symbolRegistry.getAll()).thenReturn(symbols);

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv(eq("AAPL"), anyInt());
    }

    @Test
    void fetchAndBackfillOhlcv_noDuplicatesInSymbolList() throws InterruptedException {
        // getAll() already deduplicates — verify XLK fetched exactly once
        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient, times(1)).fetchDailyOhlcv(eq("XLK"), anyInt());
    }

    @Test
    void fetchAndBackfillOhlcv_oneSymbolFails_continuesFetching() throws InterruptedException {
        List<StockSymbol> symbols = new ArrayList<>(defaultEtfSymbols());
        symbols.add(new StockSymbol("GLXY", "Galaxy Digital"));
        when(symbolRegistry.getAll()).thenReturn(symbols);
        when(twelveDataClient.fetchDailyOhlcv("GLXY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenThrow(new RuntimeException("API error"));

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv(eq("GLXY"), anyInt());
        verify(twelveDataClient).fetchDailyOhlcv(eq("SPY"), anyInt());
    }

    @Test
    void fetchAndBackfillOhlcv_withFailures_sendsTelegramSummary() throws InterruptedException {
        when(twelveDataClient.fetchDailyOhlcv("SPY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenThrow(new RuntimeException("API error"));

        ohlcvFetcher.fetchAndBackfillOhlcv();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("OHLCV Fetch Alert"));
        assertThat(message, containsString("failed"));
        assertThat(message, containsString("SPY"));
    }

    @Test
    void fetchAndBackfillOhlcv_allSucceed_noTelegramSent() throws InterruptedException {
        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(telegramGateway, never()).sendMessage(anyString());
    }

    @Test
    void fetchAndBackfillOhlcv_splitDetectedDuringRefresh_sendsTelegramAlert()
            throws InterruptedException {
        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("NFLX", "Netflix")));

        List<OhlcvRecord> storedRecords = generateRecords("NFLX", 140, 900.0);
        when(ohlcvRepository.findBySymbol("NFLX", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(storedRecords);

        // API returns split-adjusted prices (10:1 split), descending order
        List<OhlcvRecord> fetchedRecords = generateRecords("NFLX", 5, 90.0);
        when(twelveDataClient.fetchDailyOhlcv("NFLX", OhlcvFetcher.REFRESH_OUTPUT_SIZE))
                .thenReturn(fetchedRecords);

        ohlcvFetcher.fetchAndBackfillOhlcv();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("Stock Split Alert"));
        assertThat(message, containsString("NFLX"));
        assertThat(message, containsString("10:1"));
        assertThat(message, containsString("forward"));
        assertThat(message, containsString("/data reset NFLX"));
    }

    @Test
    void fetchAndBackfillOhlcv_noSplitDuringRefresh_noSplitAlert() throws InterruptedException {
        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("AAPL", "Apple")));

        List<OhlcvRecord> storedRecords = generateRecords("AAPL", 140, 180.0);
        when(ohlcvRepository.findBySymbol("AAPL", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(storedRecords);

        List<OhlcvRecord> fetchedRecords = generateRecords("AAPL", 5, 182.0);
        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.REFRESH_OUTPUT_SIZE))
                .thenReturn(fetchedRecords);

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(telegramGateway, never()).sendMessage(anyString());
    }

    @Test
    void fetchAndBackfillOhlcv_splitDetectionThrows_stillSavesRecords()
            throws InterruptedException {
        StockSplitDetector throwingDetector = mock(StockSplitDetector.class);
        when(throwingDetector.detectSplit(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("unexpected error"));

        OhlcvFetcher fetcherWithThrowingDetector =
                new OhlcvFetcher(
                        twelveDataClient,
                        ohlcvRepository,
                        symbolRegistry,
                        telegramGateway,
                        throwingDetector);
        fetcherWithThrowingDetector.setRequestDelayMs(0);

        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("NFLX", "Netflix")));

        List<OhlcvRecord> storedRecords = generateRecords("NFLX", 140, 900.0);
        when(ohlcvRepository.findBySymbol("NFLX", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(storedRecords);

        List<OhlcvRecord> fetchedRecords = generateRecords("NFLX", 5, 90.0);
        when(twelveDataClient.fetchDailyOhlcv("NFLX", OhlcvFetcher.REFRESH_OUTPUT_SIZE))
                .thenReturn(fetchedRecords);

        fetcherWithThrowingDetector.fetchAndBackfillOhlcv();

        verify(ohlcvRepository).saveAll(fetchedRecords);
    }

    @Test
    void fetchAndBackfillOhlcv_backfillMode_skipsSplitDetection() throws InterruptedException {
        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("NFLX", "Netflix")));

        // Empty DB triggers backfill mode
        when(ohlcvRepository.findBySymbol("NFLX", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(List.of());

        List<OhlcvRecord> fetchedRecords = generateRecords("NFLX", 400, 90.0);
        when(twelveDataClient.fetchDailyOhlcv("NFLX", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(fetchedRecords);

        ohlcvFetcher.fetchAndBackfillOhlcv();

        // No split alert — backfill mode skips detection
        verify(telegramGateway, never()).sendMessage(anyString());
        verify(ohlcvRepository).saveAll(fetchedRecords);
    }

    @Test
    void backfillSymbol_fetchesAndSavesRecords() {
        List<OhlcvRecord> records = generateRecords("NFLX", 400, 90.0);
        when(twelveDataClient.fetchDailyOhlcv("NFLX", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(records);

        int count = ohlcvFetcher.backfillSymbol("NFLX");

        assertThat(count, is(400));
        verify(ohlcvRepository).saveAll(records);
    }

    private List<OhlcvRecord> generateRecords(String symbol, int count) {
        return generateRecords(symbol, count, 102.0);
    }

    private List<OhlcvRecord> generateRecords(String symbol, int count, double closePrice) {
        List<OhlcvRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(
                    new OhlcvRecord(
                            symbol,
                            java.time.LocalDate.now().minusDays(count - i),
                            closePrice - 2.0,
                            closePrice + 3.0,
                            closePrice - 7.0,
                            closePrice,
                            1000000L));
        }
        return records;
    }

    /** Builds the default list of ETF StockSymbols matching SymbolRegistry.getAllEtfs(). */
    private static List<StockSymbol> defaultEtfSymbols() {
        List<StockSymbol> symbols = new ArrayList<>();
        symbols.add(
                new StockSymbol(SymbolRegistry.BENCHMARK_SYMBOL, SymbolRegistry.BENCHMARK_NAME));
        SymbolRegistry.BROAD_SECTOR_ETFS.forEach(
                (ticker, name) -> symbols.add(new StockSymbol(ticker, name)));
        SymbolRegistry.THEMATIC_ETFS.forEach(
                (ticker, name) -> symbols.add(new StockSymbol(ticker, name)));
        return symbols;
    }
}
