package org.tradelite.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.twelvedata.TwelveDataClient;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.repository.OhlcvRepository;

@Slf4j
@Service
public class OhlcvFetcher {

    static final int LOOKBACK_CALENDAR_DAYS = 200;
    static final int MIN_RECORDS_FOR_VFI = 136;
    static final int BACKFILL_OUTPUT_SIZE = 136;
    static final int REFRESH_OUTPUT_SIZE = 5;
    static final long REQUEST_DELAY_MS = 8000;

    private final TwelveDataClient twelveDataClient;
    private final OhlcvRepository ohlcvRepository;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final TelegramGateway telegramGateway;

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

    public void fetchAndBackfillOhlcv() throws InterruptedException {
        List<StockSymbol> stocks =
                stockSymbolRegistry.getAll().stream()
                        .filter(s -> !stockSymbolRegistry.isEtf(s.getTicker()))
                        .toList();

        log.info("Starting OHLCV fetch for {} symbols", stocks.size());

        List<String> failedSymbols = new ArrayList<>();
        int succeeded = 0;

        for (int i = 0; i < stocks.size(); i++) {
            if (i > 0) {
                Thread.sleep(REQUEST_DELAY_MS);
            }

            String ticker = stocks.get(i).getTicker();
            int existingRecords =
                    ohlcvRepository.findBySymbol(ticker, LOOKBACK_CALENDAR_DAYS).size();
            boolean needsBackfill = existingRecords < MIN_RECORDS_FOR_VFI;
            int outputSize = needsBackfill ? BACKFILL_OUTPUT_SIZE : REFRESH_OUTPUT_SIZE;
            String mode = needsBackfill ? "backfill" : "refresh";

            log.info("Fetching OHLCV for {} ({}/{}, {})", ticker, i + 1, stocks.size(), mode);

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
                            failedSymbols.size(), stocks.size(), failedSymbols));
        }
    }
}
