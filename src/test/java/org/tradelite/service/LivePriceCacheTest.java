package org.tradelite.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LivePriceCacheTest {

    private LivePriceCache cache;

    @BeforeEach
    void setUp() {
        cache = new LivePriceCache();
    }

    @Test
    void putAndGet() {
        cache.put("AAPL", 175.50);

        assertThat(cache.get("AAPL"), is(175.50));
    }

    @Test
    void get_unknownSymbol_returnsNull() {
        assertThat(cache.get("UNKNOWN"), is(nullValue()));
    }

    @Test
    void getAll_returnsUnmodifiableView() {
        cache.put("AAPL", 175.50);
        cache.put("RHM.DE", 1200.0);

        assertThat(cache.getAll(), aMapWithSize(2));
        assertThat(cache.getAll().get("AAPL"), is(175.50));
        assertThat(cache.getAll().get("RHM.DE"), is(1200.0));
    }

    @Test
    void put_overwritesExistingValue() {
        cache.put("AAPL", 175.50);
        cache.put("AAPL", 180.00);

        assertThat(cache.get("AAPL"), is(180.00));
    }
}
