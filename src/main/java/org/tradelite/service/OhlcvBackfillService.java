package org.tradelite.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.repository.NewlyAddedSymbolRepository;
import org.tradelite.repository.NewlyAddedSymbolRepository.NewlyAddedSymbol;

@Slf4j
@Service
@RequiredArgsConstructor
public class OhlcvBackfillService {

    static final long NEWLY_ADDED_TTL_SECONDS = 86400;
    static final int BACKFILL_BATCH_SIZE = 10;

    private final NewlyAddedSymbolRepository newlyAddedSymbolRepository;
    private final OhlcvFetcher ohlcvFetcher;
    private final SymbolRegistry symbolRegistry;

    public void backfillNewlyAddedSymbols() throws InterruptedException {
        List<NewlyAddedSymbol> pending = newlyAddedSymbolRepository.findOldest(BACKFILL_BATCH_SIZE);
        if (!pending.isEmpty()) {
            Set<String> trackedTickers =
                    symbolRegistry.getAll().stream()
                            .map(StockSymbol::getTicker)
                            .collect(Collectors.toSet());

            List<String> tickers = pending.stream().map(NewlyAddedSymbol::ticker).toList();
            List<String> removed =
                    tickers.stream().filter(t -> !trackedTickers.contains(t)).toList();

            if (!removed.isEmpty()) {
                log.info(
                        "Skipping backfill for {} symbols no longer tracked: {}",
                        removed.size(),
                        removed);
                newlyAddedSymbolRepository.deleteAll(removed);
            }

            List<String> toBackfill = tickers.stream().filter(trackedTickers::contains).toList();

            if (!toBackfill.isEmpty()) {
                List<String> succeeded = ohlcvFetcher.backfillSymbols(toBackfill);
                if (!succeeded.isEmpty()) {
                    newlyAddedSymbolRepository.deleteAll(succeeded);
                }
            }
        }
    }

    public void cleanupExpiredSymbols() {
        long cutoff = System.currentTimeMillis() / 1000 - NEWLY_ADDED_TTL_SECONDS;
        List<String> expired = newlyAddedSymbolRepository.deleteExpiredReturning(cutoff);
        for (String ticker : expired) {
            log.error(
                    "Symbol {} was not backfilled within 24h, removing from backfill queue",
                    ticker);
        }
    }
}
