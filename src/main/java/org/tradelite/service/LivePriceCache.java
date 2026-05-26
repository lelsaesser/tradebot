package org.tradelite.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LivePriceCache {

    private static final Duration TTL = Duration.ofHours(24);

    private final Map<String, PricedAt> cache = new ConcurrentHashMap<>();

    public void put(String symbol, double price) {
        cache.put(symbol, new PricedAt(price, Instant.now()));
    }

    public Double get(String symbol) {
        PricedAt entry = cache.get(symbol);
        return entry == null ? null : entry.price();
    }

    public Optional<PricedAt> getEntry(String symbol) {
        return Optional.ofNullable(cache.get(symbol));
    }

    public Map<String, Double> getAll() {
        return cache.entrySet().stream()
                .collect(
                        Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().price()));
    }

    public void evictStale() {
        evictStale(Instant.now(), TTL);
    }

    void evictStale(Instant now, Duration ttl) {
        List<String> evicted = new ArrayList<>();
        cache.forEach(
                (symbol, entry) -> {
                    if (now.isAfter(entry.updatedAt().plus(ttl)) && cache.remove(symbol, entry)) {
                        evicted.add(symbol);
                    }
                });
        if (!evicted.isEmpty()) {
            log.info("Evicted {} stale entries from LivePriceCache: {}", evicted.size(), evicted);
        }
    }

    public record PricedAt(double price, Instant updatedAt) {}
}
