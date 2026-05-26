package org.tradelite.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.tradelite.common.OhlcvRecord;

/**
 * Caching decorator over {@link SqliteOhlcvRepository}. Caches {@code findBySymbol(symbol, days)}
 * results in memory. Invalidates per-symbol entries on every write path ({@link #saveAll(List)},
 * {@link #deleteBySymbol(String)}) so consumers always observe fresh data after the underlying
 * SQLite table changes.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} + {@link ConcurrentHashMap#computeIfAbsent}. Cached
 * lists are immutable copies so consumers cannot mutate cache state.
 */
@Slf4j
@Primary
@Repository
@RequiredArgsConstructor
public class CachingOhlcvRepository implements OhlcvRepository {

    private final SqliteOhlcvRepository delegate;

    private final Map<CacheKey, List<OhlcvRecord>> cache = new ConcurrentHashMap<>();

    @Override
    public List<OhlcvRecord> findBySymbol(String symbol, int days) {
        return cache.computeIfAbsent(
                new CacheKey(symbol, days),
                key -> List.copyOf(delegate.findBySymbol(key.symbol(), key.days())));
    }

    @Override
    public void saveAll(List<OhlcvRecord> records) {
        delegate.saveAll(records);
        Set<String> touched =
                records.stream().map(OhlcvRecord::symbol).collect(Collectors.toUnmodifiableSet());
        invalidate(touched);
    }

    @Override
    public int deleteBySymbol(String symbol) {
        int deleted = delegate.deleteBySymbol(symbol);
        invalidate(Set.of(symbol));
        return deleted;
    }

    private void invalidate(Set<String> symbols) {
        if (symbols.isEmpty()) {
            return;
        }
        cache.keySet().removeIf(key -> symbols.contains(key.symbol()));
    }

    private record CacheKey(String symbol, int days) {}
}
