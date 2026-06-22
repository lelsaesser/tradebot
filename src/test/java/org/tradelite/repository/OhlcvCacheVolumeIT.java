package org.tradelite.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.tradelite.service.DailyPriceProvider;

/**
 * Regression test pinning the cache-volume invariant: when {@link DailyPriceProvider} is called
 * repeatedly for the same {@code (symbol, days)} tuple via the real Spring bean graph, the
 * underlying {@link SqliteOhlcvRepository#findBySymbol} executes at most once per tuple.
 *
 * <p>Catches regressions where the {@link CachingOhlcvRepository} decorator gets bypassed — e.g.
 * {@code @Primary} drift, accidental direct injection of {@link SqliteOhlcvRepository}, or a future
 * refactor that calls {@code SqliteOhlcvRepository} via a different path.
 *
 * <p>Single-method test by design: {@code @MockitoSpyBean} swaps the bean fresh per test method,
 * but the {@link CachingOhlcvRepository} decorator's delegate field is bound at context startup —
 * across multiple test methods the decorator would point at a stale spy reference. Combining all
 * assertions in one method keeps the spy reference consistent with the decorator's delegate.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "tradebot.api.finnhub-key=test-key",
            "tradebot.api.coingecko-key=test-key",
            "tradebot.api.twelvedata-key=test-key",
            "tradebot.telegram.group-chat-id=0",
            "tradebot.scheduling.enabled=false"
        })
@ActiveProfiles("dev")
class OhlcvCacheVolumeIT {

    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:sqlite:"
                                + tempDir.resolve("cache-volume-test.db")
                                + "?journal_mode=DELETE");
        registry.add(
                "tradebot.telegram.local-sink-file",
                () -> tempDir.resolve("telegram.log").toString());
    }

    @MockitoSpyBean private SqliteOhlcvRepository sqliteOhlcvRepository;

    @Autowired private DailyPriceProvider dailyPriceProvider;

    @Autowired private OhlcvRepository ohlcvRepository;

    @Test
    void cacheCollapsesRepeatedSqliteReadsToOneCallPerTuple() {
        // Sanity: the @Primary decorator is what consumers see.
        assertThat(ohlcvRepository).isInstanceOf(CachingOhlcvRepository.class);

        // Use a symbol guaranteed not to be in the seeded dev DB so we know we start cache-cold.
        String symbol = "ZZZ-CACHE-TEST";

        // Five reads for (ZZZ, 252) — only one should reach SQLite.
        for (int i = 0; i < 5; i++) {
            dailyPriceProvider.findDailyClosingPrices(symbol, 252);
        }
        // Four reads for (ZZZ, 35) — only one should reach SQLite.
        for (int i = 0; i < 4; i++) {
            dailyPriceProvider.findDailyClosingPrices(symbol, 35);
        }
        // Repeat (ZZZ, 252) again to confirm the previous (ZZZ, 35) reads didn't evict it.
        dailyPriceProvider.findDailyClosingPrices(symbol, 252);

        verify(sqliteOhlcvRepository, times(1)).findBySymbol(symbol, 252);
        verify(sqliteOhlcvRepository, times(1)).findBySymbol(symbol, 35);
    }
}
