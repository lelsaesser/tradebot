package org.tradelite.service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LivePriceCache {

    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    public void put(String symbol, double price) {
        cache.put(symbol, price);
    }

    public Double get(String symbol) {
        return cache.get(symbol);
    }

    public Map<String, Double> getAll() {
        return Collections.unmodifiableMap(cache);
    }
}
