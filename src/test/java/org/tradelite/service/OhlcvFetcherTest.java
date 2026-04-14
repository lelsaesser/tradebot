package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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
import org.tradelite.repository.OhlcvRepository;

@ExtendWith(MockitoExtension.class)
class OhlcvFetcherTest {

    @Mock private TwelveDataClient twelveDataClient;
    @Mock private OhlcvRepository ohlcvRepository;
    @Mock private StockSymbolRegistry stockSymbolRegistry;
    @Mock private TelegramGateway telegramGateway;

    private OhlcvFetcher ohlcvFetcher;

    @BeforeEach
    void setUp() {
        ohlcvFetcher =
                new OhlcvFetcher(
                        twelveDataClient, ohlcvRepository, stockSymbolRegistry, telegramGateway);
    }

    @Test
    void fetchAndBackfillOhlcv_emptyTable_triggersBackfill() throws InterruptedException {
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(new StockSymbol("AAPL", "Apple")));
        when(stockSymbolRegistry.isEtf("AAPL")).thenReturn(false);
        when(ohlcvRepository.findBySymbol("AAPL", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(List.of());
        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_partialData_triggersBackfill() throws InterruptedException {
        when(stockSymbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("MSFT", "Microsoft")));
        when(stockSymbolRegistry.isEtf("MSFT")).thenReturn(false);

        List<OhlcvRecord> partialRecords = generateRecords("MSFT", 100);
        when(ohlcvRepository.findBySymbol("MSFT", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(partialRecords);
        when(twelveDataClient.fetchDailyOhlcv("MSFT", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("MSFT", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_sufficientData_triggersRefresh() throws InterruptedException {
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(new StockSymbol("NVDA", "Nvidia")));
        when(stockSymbolRegistry.isEtf("NVDA")).thenReturn(false);

        List<OhlcvRecord> sufficientRecords = generateRecords("NVDA", 140);
        when(ohlcvRepository.findBySymbol("NVDA", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(sufficientRecords);
        when(twelveDataClient.fetchDailyOhlcv("NVDA", OhlcvFetcher.REFRESH_OUTPUT_SIZE))
                .thenReturn(List.of());

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("NVDA", OhlcvFetcher.REFRESH_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_filtersOutEtfs() throws InterruptedException {
        when(stockSymbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple"),
                                new StockSymbol("SPY", "S&P 500"),
                                new StockSymbol("XLK", "Technology")));
        when(stockSymbolRegistry.isEtf("AAPL")).thenReturn(false);
        when(stockSymbolRegistry.isEtf("SPY")).thenReturn(true);
        when(stockSymbolRegistry.isEtf("XLK")).thenReturn(true);
        when(ohlcvRepository.findBySymbol("AAPL", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(List.of());
        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv(eq("AAPL"), anyInt());
        verify(twelveDataClient, never()).fetchDailyOhlcv(eq("SPY"), anyInt());
        verify(twelveDataClient, never()).fetchDailyOhlcv(eq("XLK"), anyInt());
    }

    @Test
    void fetchAndBackfillOhlcv_oneSymbolFails_continuesFetching() throws InterruptedException {
        when(stockSymbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple"),
                                new StockSymbol("GLXY", "Galaxy Digital"),
                                new StockSymbol("MSFT", "Microsoft")));
        when(stockSymbolRegistry.isEtf(anyString())).thenReturn(false);
        when(ohlcvRepository.findBySymbol(anyString(), eq(OhlcvFetcher.LOOKBACK_CALENDAR_DAYS)))
                .thenReturn(List.of());

        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());
        when(twelveDataClient.fetchDailyOhlcv("GLXY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenThrow(new RuntimeException("API error"));
        when(twelveDataClient.fetchDailyOhlcv("MSFT", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());

        ohlcvFetcher.fetchAndBackfillOhlcv();

        verify(twelveDataClient).fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
        verify(twelveDataClient).fetchDailyOhlcv("GLXY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
        verify(twelveDataClient).fetchDailyOhlcv("MSFT", OhlcvFetcher.BACKFILL_OUTPUT_SIZE);
    }

    @Test
    void fetchAndBackfillOhlcv_withFailures_sendsTelegramSummary() throws InterruptedException {
        when(stockSymbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple"),
                                new StockSymbol("GLXY", "Galaxy Digital")));
        when(stockSymbolRegistry.isEtf(anyString())).thenReturn(false);
        when(ohlcvRepository.findBySymbol(anyString(), eq(OhlcvFetcher.LOOKBACK_CALENDAR_DAYS)))
                .thenReturn(List.of());

        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());
        when(twelveDataClient.fetchDailyOhlcv("GLXY", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenThrow(new RuntimeException("API error"));

        ohlcvFetcher.fetchAndBackfillOhlcv();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramGateway).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("OHLCV Fetch Alert"));
        assertThat(message, containsString("1/2 failed"));
        assertThat(message, containsString("GLXY"));
    }

    @Test
    void fetchAndBackfillOhlcv_allSucceed_noTelegramSent() throws InterruptedException {
        when(stockSymbolRegistry.getAll()).thenReturn(List.of(new StockSymbol("AAPL", "Apple")));
        when(stockSymbolRegistry.isEtf("AAPL")).thenReturn(false);
        when(ohlcvRepository.findBySymbol("AAPL", OhlcvFetcher.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(List.of());
        when(twelveDataClient.fetchDailyOhlcv("AAPL", OhlcvFetcher.BACKFILL_OUTPUT_SIZE))
                .thenReturn(List.of());

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
}
