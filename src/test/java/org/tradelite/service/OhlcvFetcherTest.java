package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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

    private OhlcvFetcher ohlcvFetcher;

    @BeforeEach
    void setUp() {
        ohlcvFetcher =
                new OhlcvFetcher(
                        twelveDataClient, ohlcvRepository, symbolRegistry, telegramGateway);
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

    private List<OhlcvRecord> generateRecords(String symbol, int count) {
        List<OhlcvRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(
                    new OhlcvRecord(
                            symbol,
                            java.time.LocalDate.now().minusDays(count - i),
                            100.0,
                            105.0,
                            95.0,
                            102.0,
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
