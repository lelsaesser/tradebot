package org.tradelite.core;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;
import org.tradelite.repository.YahooOhlcvRepository;
import org.tradelite.service.StockSymbolRegistry;

@ExtendWith(MockitoExtension.class)
class YahooOhlcvFetcherTest {

    @Mock private YahooFinanceClient yahooFinanceClient;
    @Mock private YahooOhlcvRepository yahooOhlcvRepository;
    @Mock private StockSymbolRegistry stockSymbolRegistry;

    private YahooOhlcvFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher =
                new YahooOhlcvFetcher(
                        yahooFinanceClient, yahooOhlcvRepository, stockSymbolRegistry);
    }

    @Test
    void fetchAndBackfillOhlcv_emptyTable_shouldBackfillAllSymbols() throws Exception {
        when(stockSymbolRegistry.getAllTrackedSymbols())
                .thenReturn(new LinkedHashSet<>(Set.of("AAPL", "MSFT")));
        when(yahooOhlcvRepository.findBySymbol(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<YahooOhlcvRecord> backfillRecords =
                List.of(buildRecord("AAPL", 1), buildRecord("AAPL", 2));
        when(yahooFinanceClient.fetchDailyOhlcv(anyString(), eq("6mo")))
                .thenReturn(backfillRecords);

        fetcher.fetchAndBackfillOhlcv();

        verify(yahooFinanceClient, never()).fetchDailyOhlcv(anyString(), eq("5d"));
        verify(yahooFinanceClient, times(2)).fetchDailyOhlcv(anyString(), eq("6mo"));
        verify(yahooOhlcvRepository, times(2)).saveAll(backfillRecords);
    }

    @Test
    void fetchAndBackfillOhlcv_sufficientData_shouldFetchRecentOnly() throws Exception {
        when(stockSymbolRegistry.getAllTrackedSymbols())
                .thenReturn(new LinkedHashSet<>(Set.of("AAPL")));

        List<YahooOhlcvRecord> existingRecords = buildRecordList("AAPL", 135);
        when(yahooOhlcvRepository.findBySymbol(anyString(), anyInt())).thenReturn(existingRecords);

        List<YahooOhlcvRecord> recentRecords = List.of(buildRecord("AAPL", 1));
        when(yahooFinanceClient.fetchDailyOhlcv(anyString(), eq("5d"))).thenReturn(recentRecords);

        fetcher.fetchAndBackfillOhlcv();

        verify(yahooFinanceClient, never()).fetchDailyOhlcv(anyString(), eq("6mo"));
        verify(yahooFinanceClient, times(1)).fetchDailyOhlcv(anyString(), eq("5d"));
        verify(yahooOhlcvRepository, times(1)).saveAll(recentRecords);
    }

    @Test
    void fetchAndBackfillOhlcv_mixedData_shouldBackfillOnlyInsufficient() throws Exception {
        when(stockSymbolRegistry.getAllTrackedSymbols())
                .thenReturn(new LinkedHashSet<>(List.of("AAPL", "MSFT")));

        List<YahooOhlcvRecord> sufficientRecords = buildRecordList("AAPL", 135);
        List<YahooOhlcvRecord> insufficientRecords = buildRecordList("MSFT", 50);

        when(yahooOhlcvRepository.findBySymbol(eq("AAPL"), anyInt())).thenReturn(sufficientRecords);
        when(yahooOhlcvRepository.findBySymbol(eq("MSFT"), anyInt()))
                .thenReturn(insufficientRecords);

        List<YahooOhlcvRecord> fetchedRecords = List.of(buildRecord("X", 1));
        when(yahooFinanceClient.fetchDailyOhlcv(anyString(), anyString()))
                .thenReturn(fetchedRecords);

        fetcher.fetchAndBackfillOhlcv();

        verify(yahooFinanceClient).fetchDailyOhlcv("AAPL", "5d");
        verify(yahooFinanceClient).fetchDailyOhlcv("MSFT", "6mo");
    }

    @Test
    void fetchAndBackfillOhlcv_emptyYahooResponse_shouldSkipSave() throws Exception {
        when(stockSymbolRegistry.getAllTrackedSymbols())
                .thenReturn(new LinkedHashSet<>(Set.of("AAPL")));
        when(yahooOhlcvRepository.findBySymbol(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(yahooFinanceClient.fetchDailyOhlcv(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        fetcher.fetchAndBackfillOhlcv();

        verify(yahooOhlcvRepository, never()).saveAll(anyList());
    }

    @Test
    void fetchAndBackfillOhlcv_shouldSaveResults() throws Exception {
        when(stockSymbolRegistry.getAllTrackedSymbols())
                .thenReturn(new LinkedHashSet<>(Set.of("AAPL")));
        when(yahooOhlcvRepository.findBySymbol(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<YahooOhlcvRecord> records =
                List.of(buildRecord("AAPL", 1), buildRecord("AAPL", 2), buildRecord("AAPL", 3));
        when(yahooFinanceClient.fetchDailyOhlcv(anyString(), anyString())).thenReturn(records);

        fetcher.fetchAndBackfillOhlcv();

        verify(yahooOhlcvRepository).saveAll(records);
    }

    private YahooOhlcvRecord buildRecord(String symbol, int dayOffset) {
        return new YahooOhlcvRecord(
                symbol,
                LocalDate.of(2026, 1, 1).plusDays(dayOffset),
                100.0,
                105.0,
                99.0,
                103.0,
                102.5,
                1000000L);
    }

    private List<YahooOhlcvRecord> buildRecordList(String symbol, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> buildRecord(symbol, i)).toList();
    }
}
