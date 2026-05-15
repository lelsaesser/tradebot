package org.tradelite.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    public void backfillNewlyAddedSymbols() throws InterruptedException {
        List<NewlyAddedSymbol> pending = newlyAddedSymbolRepository.findOldest(BACKFILL_BATCH_SIZE);
        if (!pending.isEmpty()) {
            List<String> tickers = pending.stream().map(NewlyAddedSymbol::ticker).toList();
            List<String> succeeded = ohlcvFetcher.backfillSymbols(tickers);
            if (!succeeded.isEmpty()) {
                newlyAddedSymbolRepository.deleteAll(succeeded);
            }
        }
    }

    public void cleanupExpiredSymbols() {
        long cutoff = System.currentTimeMillis() / 1000 - NEWLY_ADDED_TTL_SECONDS;
        List<String> expired = newlyAddedSymbolRepository.findExpired(cutoff);
        for (String ticker : expired) {
            log.error(
                    "Symbol {} was not backfilled within 24h, removing from backfill queue",
                    ticker);
        }
        if (!expired.isEmpty()) {
            newlyAddedSymbolRepository.deleteExpired(cutoff);
        }
    }
}
