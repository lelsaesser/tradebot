package org.tradelite.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.service.LivePriceCache.PricedAt;

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

    @Test
    void getEntry_returnsPriceAndTimestamp() {
        Instant before = Instant.now();
        cache.put("AAPL", 175.50);
        Instant after = Instant.now();

        Optional<PricedAt> entry = cache.getEntry("AAPL");

        assertTrue(entry.isPresent());
        assertThat(entry.get().price(), is(175.50));
        assertThat(entry.get().updatedAt(), is(greaterThanOrEqualTo(before)));
        assertThat(after, is(greaterThanOrEqualTo(entry.get().updatedAt())));
    }

    @Test
    void getEntry_unknownSymbol_returnsEmpty() {
        assertThat(cache.getEntry("UNKNOWN").isEmpty(), is(true));
    }

    @Test
    void getEntry_reflectsLatestPutAtomically() throws InterruptedException {
        cache.put("AAPL", 175.50);
        Instant firstWrite = cache.getEntry("AAPL").orElseThrow().updatedAt();

        // Sleep to ensure System.currentTimeMillis advances; Instant.now() is millisecond
        // resolution on some JVMs and back-to-back puts can otherwise share a timestamp.
        Thread.sleep(2);

        cache.put("AAPL", 180.00);
        PricedAt second = cache.getEntry("AAPL").orElseThrow();

        assertThat(second.price(), is(180.00));
        assertThat(second.updatedAt(), is(greaterThanOrEqualTo(firstWrite)));
        // The second timestamp must strictly post-date the first — otherwise put() is updating
        // price without refreshing the timestamp, which would silently freeze cache eviction.
        assertTrue(second.updatedAt().isAfter(firstWrite));
    }

    @Test
    void evictStale_removesEntriesOlderThanTtl() {
        Duration ttl = Duration.ofMinutes(30);
        Instant writeTime = Instant.now();
        cache.put("AAPL", 175.50);
        cache.put("MSFT", 420.00);

        Instant futureNow = writeTime.plus(Duration.ofMinutes(31));
        cache.evictStale(futureNow, ttl);

        assertThat(cache.get("AAPL"), is(nullValue()));
        assertThat(cache.get("MSFT"), is(nullValue()));
    }

    @Test
    void evictStale_keepsEntriesAtTtlBoundary() {
        Duration ttl = Duration.ofMinutes(30);
        Instant writeTime = Instant.now();
        cache.put("AAPL", 175.50);

        Instant exactlyAtTtl = writeTime.plus(ttl);
        cache.evictStale(exactlyAtTtl, ttl);

        assertThat(cache.get("AAPL"), is(175.50));
    }

    @Test
    void evictStale_emptyCache_isNoOp() {
        cache.evictStale(Instant.now(), Duration.ofMinutes(30));

        assertThat(cache.getAll(), is(anEmptyMap()));
    }

    @Test
    void evictStale_allFresh_keepsAll() {
        Duration ttl = Duration.ofMinutes(30);
        Instant writeTime = Instant.now();
        cache.put("AAPL", 175.50);
        cache.put("MSFT", 420.00);

        Instant fiveMinLater = writeTime.plus(Duration.ofMinutes(5));
        cache.evictStale(fiveMinLater, ttl);

        assertThat(cache.getAll(), aMapWithSize(2));
        assertThat(cache.get("AAPL"), is(175.50));
        assertThat(cache.get("MSFT"), is(420.00));
    }

    @Test
    void evictStale_allStale_emptiesCache() {
        Duration ttl = Duration.ofMinutes(30);
        Instant writeTime = Instant.now();
        cache.put("AAPL", 175.50);
        cache.put("MSFT", 420.00);
        cache.put("RHM.DE", 1200.0);

        Instant farFuture = writeTime.plus(Duration.ofHours(2));
        cache.evictStale(farFuture, ttl);

        assertThat(cache.getAll(), is(anEmptyMap()));
    }
}
