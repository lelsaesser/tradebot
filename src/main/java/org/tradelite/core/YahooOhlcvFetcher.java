package org.tradelite.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;
import org.tradelite.common.SectorEtfRegistry;
import org.tradelite.common.StockSymbol;
import org.tradelite.repository.YahooOhlcvRepository;
import org.tradelite.service.StockSymbolRegistry;

/**
 * Orchestrates Yahoo Finance OHLCV data fetching with lazy per-symbol backfill.
 *
 * <p>On each hourly run, checks every tracked symbol (ETFs + stocks) for sufficient OHLCV history.
 * Symbols with fewer than {@link #MIN_RECORDS_FOR_VFI} records get a 6-month backfill; symbols with
 * enough history get a short 5-day refresh. This lazy approach handles newly-added symbols
 * automatically — no restart needed.
 */
@Slf4j
@Component
public class YahooOhlcvFetcher {

    static final int BACKFILL_CHECK_DAYS = 200;
    static final int MIN_RECORDS_FOR_VFI = 131;
    static final long REQUEST_DELAY_MS = 500;

    /**
     * Yahoo Finance "5d" range with interval=1d returns the last ~5 daily OHLCV bars. We use "5d"
     * instead of "1d" because Yahoo's "1d" range returns intraday minute-level candles, not a
     * single daily bar. The extra 4 days are harmless — upsert (INSERT OR REPLACE) overwrites
     * identical existing records.
     */
    static final String RANGE_RECENT = "5d";

    static final String RANGE_BACKFILL = "6mo";

    private final YahooFinanceClient yahooFinanceClient;
    private final YahooOhlcvRepository yahooOhlcvRepository;
    private final StockSymbolRegistry stockSymbolRegistry;

    @Autowired
    public YahooOhlcvFetcher(
            YahooFinanceClient yahooFinanceClient,
            YahooOhlcvRepository yahooOhlcvRepository,
            StockSymbolRegistry stockSymbolRegistry) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.yahooOhlcvRepository = yahooOhlcvRepository;
        this.stockSymbolRegistry = stockSymbolRegistry;
    }

    /**
     * Fetches OHLCV data for all tracked symbols, backfilling where needed.
     *
     * <p>For each symbol: if fewer than {@value #MIN_RECORDS_FOR_VFI} records exist in the last
     * {@value #BACKFILL_CHECK_DAYS} days, fetches 6 months of history (backfill). Otherwise fetches
     * only the last 5 days to update the current trading day's bar.
     */
    public void fetchAndBackfillOhlcv() throws InterruptedException {
        Set<String> symbols = collectAllSymbols();
        int backfilledCount = 0;
        int updatedCount = 0;

        for (String symbol : symbols) {
            int existingRecords =
                    yahooOhlcvRepository.findBySymbol(symbol, BACKFILL_CHECK_DAYS).size();
            boolean needsBackfill = existingRecords < MIN_RECORDS_FOR_VFI;

            String range = needsBackfill ? RANGE_BACKFILL : RANGE_RECENT;
            List<YahooOhlcvRecord> records = yahooFinanceClient.fetchDailyOhlcv(symbol, range);

            if (!records.isEmpty()) {
                yahooOhlcvRepository.saveAll(records);
            }

            if (needsBackfill) {
                log.info(
                        "Backfilled {} with {} records (had {} existing)",
                        symbol,
                        records.size(),
                        existingRecords);
                backfilledCount++;
            } else {
                updatedCount++;
            }

            Thread.sleep(REQUEST_DELAY_MS);
        }

        log.info(
                "Yahoo OHLCV fetch complete: {} symbols total, {} backfilled, {} updated",
                symbols.size(),
                backfilledCount,
                updatedCount);
    }

    Set<String> collectAllSymbols() {
        Set<String> symbols = new LinkedHashSet<>(SectorEtfRegistry.allEtfs().keySet());
        stockSymbolRegistry.getAll().stream().map(StockSymbol::getTicker).forEach(symbols::add);
        return symbols;
    }
}
