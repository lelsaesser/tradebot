package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.OhlcvRecord;

@ExtendWith(MockitoExtension.class)
class CachingOhlcvRepositoryTest {

    @Mock private SqliteOhlcvRepository delegate;

    private CachingOhlcvRepository cache;

    @BeforeEach
    void setUp() {
        cache = new CachingOhlcvRepository(delegate);
    }

    @Test
    void findBySymbol_cacheMiss_delegatesAndPopulates() {
        List<OhlcvRecord> records = List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10)));
        when(delegate.findBySymbol("AAPL", 252)).thenReturn(records);

        List<OhlcvRecord> result = cache.findBySymbol("AAPL", 252);

        assertEquals(records, result);
        verify(delegate).findBySymbol("AAPL", 252);
    }

    @Test
    void findBySymbol_cacheHit_doesNotDelegate() {
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("AAPL", 252);

        verify(delegate, times(1)).findBySymbol("AAPL", 252);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void findBySymbol_emptyResultIsCached() {
        when(delegate.findBySymbol("UNKNOWN", 252)).thenReturn(List.of());

        List<OhlcvRecord> first = cache.findBySymbol("UNKNOWN", 252);
        List<OhlcvRecord> second = cache.findBySymbol("UNKNOWN", 252);

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        verify(delegate, times(1)).findBySymbol("UNKNOWN", 252);
    }

    @Test
    void findBySymbol_differentDaysAreSeparateEntries() {
        when(delegate.findBySymbol("AAPL", 35))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);

        verify(delegate, times(1)).findBySymbol("AAPL", 35);
        verify(delegate, times(1)).findBySymbol("AAPL", 252);
    }

    @Test
    void saveAll_invalidatesTouchedSymbolsOnly() {
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("MSFT", 252))
                .thenReturn(List.of(ohlcvRecord("MSFT", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);

        cache.saveAll(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11))));

        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);

        verify(delegate, times(2)).findBySymbol("AAPL", 252);
        verify(delegate, times(1)).findBySymbol("MSFT", 252);
    }

    @Test
    void saveAll_invalidatesAllDaysVariantsForTouchedSymbol() {
        when(delegate.findBySymbol("AAPL", 35))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);

        cache.saveAll(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11))));

        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);

        verify(delegate, times(2)).findBySymbol("AAPL", 35);
        verify(delegate, times(2)).findBySymbol("AAPL", 252);
    }

    @Test
    void saveAll_multiSymbolBatchInvalidatesAllTouchedSymbols() {
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("MSFT", 252))
                .thenReturn(List.of(ohlcvRecord("MSFT", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("GOOG", 252))
                .thenReturn(List.of(ohlcvRecord("GOOG", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);
        cache.findBySymbol("GOOG", 252);

        cache.saveAll(
                List.of(
                        ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11)),
                        ohlcvRecord("MSFT", LocalDate.of(2026, 4, 11))));

        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);
        cache.findBySymbol("GOOG", 252);

        verify(delegate, times(2)).findBySymbol("AAPL", 252);
        verify(delegate, times(2)).findBySymbol("MSFT", 252);
        verify(delegate, times(1)).findBySymbol("GOOG", 252);
    }

    @Test
    void saveAll_emptyListDoesNotInvalidate() {
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));

        cache.findBySymbol("AAPL", 252);

        cache.saveAll(List.of());

        cache.findBySymbol("AAPL", 252);

        verify(delegate, times(1)).findBySymbol("AAPL", 252);
        verify(delegate).saveAll(List.of());
    }

    @Test
    void saveAll_delegatesBeforeInvalidating() {
        cache.saveAll(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11))));

        verify(delegate).saveAll(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11))));
    }

    @Test
    void deleteBySymbol_invalidatesAllEntriesForSymbol() {
        when(delegate.findBySymbol("AAPL", 35))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));
        when(delegate.findBySymbol("MSFT", 252))
                .thenReturn(List.of(ohlcvRecord("MSFT", LocalDate.of(2026, 4, 10))));
        when(delegate.deleteBySymbol("AAPL")).thenReturn(2);

        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);

        int deleted = cache.deleteBySymbol("AAPL");

        cache.findBySymbol("AAPL", 35);
        cache.findBySymbol("AAPL", 252);
        cache.findBySymbol("MSFT", 252);

        assertEquals(2, deleted);
        verify(delegate, times(2)).findBySymbol("AAPL", 35);
        verify(delegate, times(2)).findBySymbol("AAPL", 252);
        verify(delegate, times(1)).findBySymbol("MSFT", 252);
        verify(delegate).deleteBySymbol("AAPL");
    }

    @Test
    void cachedListIsImmutable() {
        List<OhlcvRecord> mutableSource = new ArrayList<>();
        mutableSource.add(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10)));
        when(delegate.findBySymbol("AAPL", 252)).thenReturn(mutableSource);

        List<OhlcvRecord> cached = cache.findBySymbol("AAPL", 252);

        assertThrows(
                UnsupportedOperationException.class,
                () -> cached.add(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11))));
    }

    @Test
    void cachedListIsStableAcrossReads() {
        when(delegate.findBySymbol("AAPL", 252))
                .thenReturn(List.of(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10))));

        List<OhlcvRecord> first = cache.findBySymbol("AAPL", 252);
        List<OhlcvRecord> second = cache.findBySymbol("AAPL", 252);

        assertSame(first, second);
        verify(delegate, times(1)).findBySymbol("AAPL", 252);
    }

    @Test
    void mutationOfDelegateSourceDoesNotAffectCachedEntry() {
        List<OhlcvRecord> mutableSource = new ArrayList<>();
        mutableSource.add(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 10)));
        when(delegate.findBySymbol("AAPL", 252)).thenReturn(mutableSource);

        List<OhlcvRecord> cached = cache.findBySymbol("AAPL", 252);
        mutableSource.add(ohlcvRecord("AAPL", LocalDate.of(2026, 4, 11)));

        assertEquals(1, cached.size());
    }

    private static OhlcvRecord ohlcvRecord(String symbol, LocalDate date) {
        return new OhlcvRecord(symbol, date, 100.0, 105.0, 99.0, 103.0, 1_000_000L);
    }
}
