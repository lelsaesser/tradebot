package org.tradelite.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.twelvedata.TwelveDataClient;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.SectorEtfRegistry;
import org.tradelite.common.StockSymbol;
import org.tradelite.repository.OhlcvRepository;

@SuppressWarnings("SameParameterValue")
@Slf4j
@Service
public class OhlcvFetcher {

    static final int LOOKBACK_CALENDAR_DAYS = 600;
    static final int MIN_RECORDS_FOR_BACKFILL = 136;
    static final int BACKFILL_OUTPUT_SIZE = 400;
    static final int REFRESH_OUTPUT_SIZE = 5;
    static final long DEFAULT_REQUEST_DELAY_MS = 8000;

    private final TwelveDataClient twelveDataClient;
    private final OhlcvRepository ohlcvRepository;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final TelegramGateway telegramGateway;
    private long requestDelayMs = DEFAULT_REQUEST_DELAY_MS;

    @Autowired
    public OhlcvFetcher(
            TwelveDataClient twelveDataClient,
            OhlcvRepository ohlcvRepository,
            StockSymbolRegistry stockSymbolRegistry,
            TelegramGateway telegramGateway) {
        this.twelveDataClient = twelveDataClient;
        this.ohlcvRepository = ohlcvRepository;
        this.stockSymbolRegistry = stockSymbolRegistry;
        this.telegramGateway = telegramGateway;
    }

    void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public void fetchAndBackfillOhlcv() throws InterruptedException {
        List<String> symbols = buildSymbolList();

        log.info("Starting OHLCV fetch for {} symbols", symbols.size());

        List<String> failedSymbols = new ArrayList<>();
        int succeeded = 0;

        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) {
                //noinspection BusyWait
                Thread.sleep(requestDelayMs);
            }

            String ticker = symbols.get(i);
            int existingRecords =
                    ohlcvRepository.findBySymbol(ticker, LOOKBACK_CALENDAR_DAYS).size();
            boolean needsBackfill = existingRecords < MIN_RECORDS_FOR_BACKFILL;
            int outputSize = needsBackfill ? BACKFILL_OUTPUT_SIZE : REFRESH_OUTPUT_SIZE;
            String mode = needsBackfill ? "backfill" : "refresh";

            log.info("Fetching OHLCV for {} ({}/{}, {})", ticker, i + 1, symbols.size(), mode);

            try {
                List<OhlcvRecord> records = twelveDataClient.fetchDailyOhlcv(ticker, outputSize);
                ohlcvRepository.saveAll(records);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to fetch OHLCV for {}: {}", ticker, e.getMessage());
                failedSymbols.add(ticker);
            }
        }

        log.info(
                "OHLCV fetch complete: {} succeeded, {} failed{}",
                succeeded,
                failedSymbols.size(),
                failedSymbols.isEmpty() ? "" : " " + failedSymbols);

        if (!failedSymbols.isEmpty()) {
            telegramGateway.sendMessage(
                    String.format(
                            "*OHLCV Fetch Alert*%n%d/%d failed %s",
                            failedSymbols.size(), symbols.size(), failedSymbols));
        }
    }

    private List<String> buildSymbolList() {
        Set<String> symbols =
                new LinkedHashSet<>(SectorEtfRegistry.allEtfsWithBenchmark().keySet());

        for (StockSymbol stock : stockSymbolRegistry.getAll()) {
            if (!stockSymbolRegistry.isEtf(stock.getTicker())) {
                symbols.add(stock.getTicker());
            }
        }

        return new ArrayList<>(symbols);
    }
}
