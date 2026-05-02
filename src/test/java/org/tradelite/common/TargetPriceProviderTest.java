package org.tradelite.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import org.tradelite.core.IgnoreReason;
import org.tradelite.repository.SqliteIgnoredSymbolRepository;
import org.tradelite.repository.TargetPriceRepository;

@SuppressWarnings("ResultOfMethodCallIgnored")
class TargetPriceProviderTest {

    private TargetPriceProvider targetPriceProvider;
    private TargetPriceRepository targetPriceRepository;
    private String testDbPath;

    @BeforeEach
    void setUp() {
        testDbPath = "target/test-target-price-provider-" + UUID.randomUUID() + ".db";
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + testDbPath);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS ignored_symbols (
                    symbol          TEXT    NOT NULL,
                    reason          TEXT    NOT NULL,
                    ignored_at      INTEGER NOT NULL,
                    alert_threshold INTEGER,
                    PRIMARY KEY (symbol, reason)
                )
                """);
        SqliteIgnoredSymbolRepository ignoredSymbolRepository =
                new SqliteIgnoredSymbolRepository(jdbcTemplate);
        targetPriceRepository = mock(TargetPriceRepository.class);
        targetPriceProvider =
                new TargetPriceProvider(targetPriceRepository, ignoredSymbolRepository);
    }

    @AfterEach
    void tearDown() {
        new File(testDbPath).delete();
    }

    @Test
    void addIgnoreSymbol_symbolNotExists() {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT),
                is(true));

        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT),
                is(true));

        // Adding same symbol/reason again should not break anything
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);

        // Both reasons for SOLANA should be ignored
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.SELL_ALERT),
                is(true));

        // Adding BUY_ALERT for AMZN
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.BUY_ALERT);
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.BUY_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT),
                is(true));
    }

    @Test
    void isSymbolIgnored() {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);

        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.SELL_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.BUY_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMD", "AMD"), IgnoreReason.BUY_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.BITCOIN, IgnoreReason.SELL_ALERT),
                is(false));
    }

    @Test
    void isSymbolIgnored_withThreshold() {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.CHANGE_PERCENT_ALERT, 5);

        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        CoinId.SOLANA, IgnoreReason.CHANGE_PERCENT_ALERT, 5),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        CoinId.SOLANA, IgnoreReason.CHANGE_PERCENT_ALERT, 10),
                is(false));
    }

    @Test
    @SuppressWarnings("java:S2925") // Sleep is used to test the cleanup functionality
    void cleanupIgnoreSymbols() throws InterruptedException {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);

        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT),
                is(true));

        Thread.sleep(3000);
        targetPriceProvider.cleanupIgnoreSymbols(1L);

        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT),
                is(false));

        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("META", "Meta Platforms"), IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("PLTR", "Palantir"), IgnoreReason.BUY_ALERT);

        Thread.sleep(3500);

        // Add fresh entries for BTC and META (different reasons)
        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("META", "Meta Platforms"), IgnoreReason.BUY_ALERT);

        targetPriceProvider.cleanupIgnoreSymbols(1L);

        // Old entries should be cleaned up
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.BITCOIN, IgnoreReason.BUY_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("META", "Meta Platforms"), IgnoreReason.SELL_ALERT),
                is(false));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("PLTR", "Palantir"), IgnoreReason.BUY_ALERT),
                is(false));

        // Fresh entries should still exist
        assertThat(
                targetPriceProvider.isSymbolIgnored(CoinId.BITCOIN, IgnoreReason.SELL_ALERT),
                is(true));
        assertThat(
                targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("META", "Meta Platforms"), IgnoreReason.BUY_ALERT),
                is(true));
    }

    @Test
    void updateTargetPrice_ok() {
        List<TargetPrice> prices = new ArrayList<>();
        prices.add(new TargetPrice(CoinId.SOLANA.getName(), 100.0, 150.0));
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(prices);

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 160.0, 200.0, AssetType.COIN);

        verify(targetPriceRepository)
                .save(
                        argThat(
                                tp ->
                                        tp.getSymbol().equals(CoinId.SOLANA.getName())
                                                && tp.getBuyTarget() == 160.0
                                                && tp.getSellTarget() == 200.0),
                        eq(AssetType.COIN));
    }

    @Test
    void updateTargetPrice_withNullBuyTarget() {
        List<TargetPrice> prices = new ArrayList<>();
        prices.add(new TargetPrice(CoinId.SOLANA.getName(), 100.0, 150.0));
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(prices);

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, null, 1105.0, AssetType.COIN);

        verify(targetPriceRepository)
                .save(
                        argThat(
                                tp ->
                                        tp.getSymbol().equals(CoinId.SOLANA.getName())
                                                && tp.getBuyTarget() == 100.0
                                                && tp.getSellTarget() == 1105.0),
                        eq(AssetType.COIN));
    }

    @Test
    void updateTargetPrice_withNullSellTarget() {
        List<TargetPrice> prices = new ArrayList<>();
        prices.add(new TargetPrice(CoinId.SOLANA.getName(), 100.0, 150.0));
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(prices);

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 165.0, null, AssetType.COIN);

        verify(targetPriceRepository)
                .save(
                        argThat(
                                tp ->
                                        tp.getSymbol().equals(CoinId.SOLANA.getName())
                                                && tp.getBuyTarget() == 165.0
                                                && tp.getSellTarget() == 150.0),
                        eq(AssetType.COIN));
    }

    @Test
    void updateTargetPrice_symbolNotFound_nothingSaved() {
        when(targetPriceRepository.findByAssetType(AssetType.STOCK)).thenReturn(new ArrayList<>());

        targetPriceProvider.updateTargetPrice(
                new StockSymbol("GOOG", "Google"), 160.0, 200.0, AssetType.STOCK);

        verify(targetPriceRepository, never()).save(any(), any());
    }

    @Test
    void addTargetPrice_ok() {
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(new ArrayList<>());

        TargetPrice targetPrice = new TargetPrice(CoinId.DOGE.getName(), 0.0, 0.0);
        boolean result = targetPriceProvider.addTargetPrice(targetPrice, AssetType.COIN);

        assertThat(result, is(true));
        verify(targetPriceRepository).save(targetPrice, AssetType.COIN);
    }

    @Test
    void addTargetPrice_symbolAlreadyExists_nothingAdded() {
        List<TargetPrice> existing = new ArrayList<>();
        existing.add(new TargetPrice(CoinId.SOLANA.getName(), 100.0, 150.0));
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(existing);

        TargetPrice targetPrice = new TargetPrice(CoinId.SOLANA.getName(), 0.0, 0.0);
        boolean result = targetPriceProvider.addTargetPrice(targetPrice, AssetType.COIN);

        assertThat(result, is(false));
        verify(targetPriceRepository, never()).save(any(), any());
    }

    @Test
    void removeSymbolFromTargetPrices_symbolPresent_isRemoved() {
        when(targetPriceRepository.deleteBySymbolAndType(CoinId.SOLANA.getName(), AssetType.COIN))
                .thenReturn(true);

        boolean result =
                targetPriceProvider.removeSymbolFromTargetPrices(
                        CoinId.SOLANA.getName(), AssetType.COIN);

        assertThat(result, is(true));
        verify(targetPriceRepository)
                .deleteBySymbolAndType(CoinId.SOLANA.getName(), AssetType.COIN);
    }

    @Test
    void removeSymbolFromTargetPrices_symbolNotPresent_returnsFalse() {
        when(targetPriceRepository.deleteBySymbolAndType(CoinId.DOGE.getName(), AssetType.COIN))
                .thenReturn(false);

        boolean result =
                targetPriceProvider.removeSymbolFromTargetPrices(
                        CoinId.DOGE.getName(), AssetType.COIN);

        assertThat(result, is(false));
    }

    @Test
    void isSymbolIgnored_perReasonTtl_usesReasonSpecificTtl() {
        StockSymbol stock = new StockSymbol("AAPL", "Apple");
        targetPriceProvider.addIgnoredSymbol(stock, IgnoreReason.PULLBACK_BUY_ALERT);

        assertThat(
                targetPriceProvider.isSymbolIgnored(stock, IgnoreReason.PULLBACK_BUY_ALERT),
                is(true));

        // Verify the TTL values are reason-specific
        assertThat(IgnoreReason.BUY_ALERT.getTtlSeconds(), is(3600L * 12));
        assertThat(IgnoreReason.PULLBACK_BUY_ALERT.getTtlSeconds(), is(3600L * 8));
    }

    @Test
    void getStockTargetPrices_ok() {
        List<TargetPrice> expected = List.of(new TargetPrice("AAPL", 150.0, 200.0));
        when(targetPriceRepository.findByAssetType(AssetType.STOCK)).thenReturn(expected);

        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        assertThat(targetPrices, notNullValue());
        assertThat(targetPrices, is(expected));
        verify(targetPriceRepository).findByAssetType(AssetType.STOCK);
    }

    @Test
    void getCoinTargetPrices_ok() {
        List<TargetPrice> expected = List.of(new TargetPrice("SOL", 100.0, 150.0));
        when(targetPriceRepository.findByAssetType(AssetType.COIN)).thenReturn(expected);

        List<TargetPrice> targetPrices = targetPriceProvider.getCoinTargetPrices();

        assertThat(targetPrices, notNullValue());
        assertThat(targetPrices, is(expected));
        verify(targetPriceRepository).findByAssetType(AssetType.COIN);
    }
}
