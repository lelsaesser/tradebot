package org.tradelite.service;

import java.util.ArrayList;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.twelvedata.TwelveDataClient;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.repository.OhlcvRepository;

@SuppressWarnings("SameParameterValue")
@Slf4j
@Service
public class OhlcvFetcher {

    static final int LOOKBACK_CALENDAR_DAYS = 600;
    static final int MIN_RECORDS_FOR_BACKFILL = 136;
    static final int BACKFILL_OUTPUT_SIZE = 400;
    static final int REFRESH_OUTPUT_SIZE = 5;
    static final long DEFAULT_REQUEST_DELAY_MS = 9000;
    static final long RATE_LIMIT_WAIT_MS = 61_000;
    static final int MAX_RETRIES = 1;

    private final TwelveDataClient twelveDataClient;
    private final OhlcvRepository ohlcvRepository;
    private final SymbolRegistry symbolRegistry;
    private final TelegramGateway telegramGateway;
    private final StockSplitDetector stockSplitDetector;
    @Setter private long requestDelayMs = DEFAULT_REQUEST_DELAY_MS;
    @Setter private long rateLimitWaitMs = RATE_LIMIT_WAIT_MS;

    @Autowired
    public OhlcvFetcher(
            TwelveDataClient twelveDataClient,
            OhlcvRepository ohlcvRepository,
            SymbolRegistry symbolRegistry,
            TelegramGateway telegramGateway,
            StockSplitDetector stockSplitDetector) {
        this.twelveDataClient = twelveDataClient;
        this.ohlcvRepository = ohlcvRepository;
        this.symbolRegistry = symbolRegistry;
        this.telegramGateway = telegramGateway;
        this.stockSplitDetector = stockSplitDetector;
    }

    public void fetchAndBackfillOhlcv() throws InterruptedException {
        fetchAndBackfillOhlcv(Integer.MAX_VALUE);
    }

    public void fetchAndBackfillOhlcv(int maxSymbols) throws InterruptedException {
        List<String> allSymbols =
                symbolRegistry.getAll().stream().map(StockSymbol::getTicker).toList();
        List<String> symbols =
                maxSymbols < allSymbols.size() ? allSymbols.subList(0, maxSymbols) : allSymbols;

        log.info("Starting OHLCV fetch for {} symbols", symbols.size());

        List<String> failedSymbols = new ArrayList<>();
        int succeeded = 0;

        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) {
                //noinspection BusyWait
                Thread.sleep(requestDelayMs);
            }

            String ticker = symbols.get(i);
            List<OhlcvRecord> existingRecords =
                    ohlcvRepository.findBySymbol(ticker, LOOKBACK_CALENDAR_DAYS);
            boolean needsBackfill = existingRecords.size() < MIN_RECORDS_FOR_BACKFILL;
            int outputSize = needsBackfill ? BACKFILL_OUTPUT_SIZE : REFRESH_OUTPUT_SIZE;
            String mode = needsBackfill ? "backfill" : "refresh";

            log.info("Fetching OHLCV for {} ({}/{}, {})", ticker, i + 1, symbols.size(), mode);

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    List<OhlcvRecord> records =
                            twelveDataClient.fetchDailyOhlcv(ticker, outputSize);

                    if (!needsBackfill && !records.isEmpty() && !existingRecords.isEmpty()) {
                        try {
                            checkForStockSplit(ticker, existingRecords, records);
                        } catch (Exception e) {
                            log.warn("Split detection failed for {}: {}", ticker, e.getMessage());
                        }
                    }

                    ohlcvRepository.saveAll(records);
                    succeeded++;
                    break;
                } catch (Exception e) {
                    if (isRateLimitError(e) && attempt < MAX_RETRIES) {
                        log.warn(
                                "Rate limit hit for {}, waiting {}ms before retry",
                                ticker,
                                rateLimitWaitMs);
                        //noinspection BusyWait
                        Thread.sleep(rateLimitWaitMs);
                    } else {
                        log.error("Failed to fetch OHLCV for {}: {}", ticker, e.getMessage());
                        failedSymbols.add(ticker);
                        break;
                    }
                }
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

    /**
     * Fetches a full backfill (400 records) for a single symbol and saves to the repository. Used
     * by the /data reset command after deleting existing data.
     *
     * @return number of records fetched and saved
     */
    public int backfillSymbol(String ticker) {
        log.info("Backfilling OHLCV for {}", ticker);
        List<OhlcvRecord> records = twelveDataClient.fetchDailyOhlcv(ticker, BACKFILL_OUTPUT_SIZE);
        ohlcvRepository.saveAll(records);
        log.info("Backfill complete for {}: {} records saved", ticker, records.size());
        return records.size();
    }

    static boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("You have run out of API credits");
    }

    private void checkForStockSplit(
            String ticker, List<OhlcvRecord> existingRecords, List<OhlcvRecord> fetchedRecords) {
        double lastStoredClose = existingRecords.getLast().close();
        double oldestFetchedClose = fetchedRecords.getLast().close();

        stockSplitDetector
                .detectSplit(lastStoredClose, oldestFetchedClose)
                .ifPresent(
                        result ->
                                telegramGateway.sendMessage(
                                        String.format(
                                                "*Stock Split Alert*%n"
                                                        + "%s: possible %d:1 %s split detected%n"
                                                        + "Stored close: %.2f → Fetched close:"
                                                        + " %.2f%n"
                                                        + "Run `/data reset %s` to fix historical"
                                                        + " data",
                                                ticker,
                                                result.factor(),
                                                result.direction().name().toLowerCase(),
                                                lastStoredClose,
                                                oldestFetchedClose,
                                                ticker)));
    }
}
