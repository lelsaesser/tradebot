package org.tradelite.service;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.repository.NewlyAddedSymbolRepository;
import org.tradelite.repository.NewlyAddedSymbolRepository.NewlyAddedSymbol;

@ExtendWith(MockitoExtension.class)
class OhlcvBackfillServiceTest {

    @Mock private NewlyAddedSymbolRepository newlyAddedSymbolRepository;
    @Mock private OhlcvFetcher ohlcvFetcher;
    @Mock private SymbolRegistry symbolRegistry;

    private OhlcvBackfillService service;

    @BeforeEach
    void setUp() {
        service = new OhlcvBackfillService(newlyAddedSymbolRepository, ohlcvFetcher, symbolRegistry);
    }

    @Test
    void backfillNewlyAddedSymbols_noPending_doesNothing() throws InterruptedException {
        when(newlyAddedSymbolRepository.findOldest(OhlcvBackfillService.BACKFILL_BATCH_SIZE))
                .thenReturn(List.of());

        service.backfillNewlyAddedSymbols();

        verify(ohlcvFetcher, never()).backfillSymbols(anyList());
        verify(newlyAddedSymbolRepository, never()).deleteAll(anyList());
    }

    @Test
    void backfillNewlyAddedSymbols_withPending_callsFetcherAndDeletesSucceeded()
            throws InterruptedException {
        List<NewlyAddedSymbol> pending =
                List.of(new NewlyAddedSymbol("AAPL", 1000L), new NewlyAddedSymbol("MSFT", 1001L));
        when(newlyAddedSymbolRepository.findOldest(OhlcvBackfillService.BACKFILL_BATCH_SIZE))
                .thenReturn(pending);
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple"),
                                new StockSymbol("MSFT", "Microsoft")));
        when(ohlcvFetcher.backfillSymbols(List.of("AAPL", "MSFT")))
                .thenReturn(List.of("AAPL", "MSFT"));

        service.backfillNewlyAddedSymbols();

        verify(ohlcvFetcher).backfillSymbols(List.of("AAPL", "MSFT"));
        verify(newlyAddedSymbolRepository).deleteAll(List.of("AAPL", "MSFT"));
    }

    @Test
    void backfillNewlyAddedSymbols_partialSuccess_deletesOnlySucceeded()
            throws InterruptedException {
        List<NewlyAddedSymbol> pending =
                List.of(new NewlyAddedSymbol("AAPL", 1000L), new NewlyAddedSymbol("BAD", 1001L));
        when(newlyAddedSymbolRepository.findOldest(OhlcvBackfillService.BACKFILL_BATCH_SIZE))
                .thenReturn(pending);
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple"),
                                new StockSymbol("BAD", "Bad Inc")));
        when(ohlcvFetcher.backfillSymbols(List.of("AAPL", "BAD"))).thenReturn(List.of("AAPL"));

        service.backfillNewlyAddedSymbols();

        verify(newlyAddedSymbolRepository).deleteAll(List.of("AAPL"));
    }

    @Test
    void backfillNewlyAddedSymbols_allFail_doesNotDelete() throws InterruptedException {
        List<NewlyAddedSymbol> pending = List.of(new NewlyAddedSymbol("BAD", 1000L));
        when(newlyAddedSymbolRepository.findOldest(OhlcvBackfillService.BACKFILL_BATCH_SIZE))
                .thenReturn(pending);
        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("BAD", "Bad Inc")));
        when(ohlcvFetcher.backfillSymbols(List.of("BAD"))).thenReturn(List.of());

        service.backfillNewlyAddedSymbols();

        verify(newlyAddedSymbolRepository, never()).deleteAll(anyList());
    }

    @Test
    void backfillNewlyAddedSymbols_removedSymbol_deletesFromQueueWithoutFetching()
            throws InterruptedException {
        List<NewlyAddedSymbol> pending = List.of(new NewlyAddedSymbol("REMOVED", 1000L));
        when(newlyAddedSymbolRepository.findOldest(OhlcvBackfillService.BACKFILL_BATCH_SIZE))
                .thenReturn(pending);
        when(symbolRegistry.getAll())
                .thenReturn(List.of(new StockSymbol("AAPL", "Apple")));

        service.backfillNewlyAddedSymbols();

        verify(newlyAddedSymbolRepository).deleteAll(List.of("REMOVED"));
        verify(ohlcvFetcher, never()).backfillSymbols(anyList());
    }

    @Test
    void cleanupExpiredSymbols_noExpired_doesNothing() {
        when(newlyAddedSymbolRepository.deleteExpiredReturning(anyLong())).thenReturn(List.of());

        service.cleanupExpiredSymbols();

        verify(newlyAddedSymbolRepository).deleteExpiredReturning(anyLong());
    }

    @Test
    void cleanupExpiredSymbols_withExpired_deletesAndLogs() {
        when(newlyAddedSymbolRepository.deleteExpiredReturning(anyLong()))
                .thenReturn(List.of("OLD1", "OLD2"));

        service.cleanupExpiredSymbols();

        verify(newlyAddedSymbolRepository).deleteExpiredReturning(anyLong());
    }
}
