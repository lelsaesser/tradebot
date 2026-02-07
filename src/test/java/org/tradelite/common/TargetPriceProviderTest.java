package org.tradelite.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.core.IgnoreReason;

@ExtendWith(MockitoExtension.class)
class TargetPriceProviderTest {

    private static final String FILE_PATH = "config/target-prices-test.json";

    private TargetPriceProvider targetPriceProvider;

    @BeforeEach
    void setUp() {
        targetPriceProvider = new TargetPriceProvider(new ObjectMapper());
    }

    private boolean fileContainsSymbol(TickerSymbol symbol) {
        List<TargetPrice> targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        boolean found = false;
        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equalsIgnoreCase(symbol.getName())) {
                found = true;
                assertThat(targetPrice.getBuyTarget(), greaterThanOrEqualTo(0.0));
                assertThat(targetPrice.getSellTarget(), greaterThanOrEqualTo(0.0));
                break;
            }
        }
        return found;
    }

    @Test
    void addIgnoreSymbol_symbolNotExists() {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(1));

        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));

        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));
        assertThat(
                targetPriceProvider.ignoredSymbols.get(CoinId.SOLANA.getName()).getIgnoreTimes(),
                aMapWithSize(2));
        assertThat(
                targetPriceProvider
                        .ignoredSymbols
                        .get(new StockSymbol("AMZN", "Amazon").getName())
                        .getIgnoreTimes(),
                aMapWithSize(1));

        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("AMZN", "Amazon"), IgnoreReason.BUY_ALERT);
        assertThat(
                targetPriceProvider
                        .ignoredSymbols
                        .get(new StockSymbol("AMZN", "Amazon").getName())
                        .getIgnoreTimes(),
                aMapWithSize(2));
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

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));
        Thread.sleep(3000);
        targetPriceProvider.cleanupIgnoreSymbols(1L);
        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(0));

        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("META", "Meta Platforms"), IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("PLTR", "Palantir"), IgnoreReason.BUY_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(3));
        Thread.sleep(3500);
        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(
                new StockSymbol("META", "Meta Platforms"), IgnoreReason.BUY_ALERT);
        targetPriceProvider.cleanupIgnoreSymbols(1L);
        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));
    }

    @Test
    void updateTargetPrice_ok() {
        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 160.0, 200.0, FILE_PATH);
        List<TargetPrice> targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), is(160.0));
                assertThat(targetPrice.getSellTarget(), is(200.0));
            }
        }

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 250.0, 1100.0, FILE_PATH);
        targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), is(250.0));
                assertThat(targetPrice.getSellTarget(), is(1100.0));
            }
        }
    }

    @Test
    void updateTargetPrice_withNullValues() {
        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, 165.0, null, FILE_PATH);
        List<TargetPrice> targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), is(165.0));
                assertThat(targetPrice.getSellTarget(), greaterThan(0.0));
            }
        }

        targetPriceProvider.updateTargetPrice(CoinId.SOLANA, null, 1105.0, FILE_PATH);
        targetPrices = targetPriceProvider.loadTargetPrices(FILE_PATH);

        for (TargetPrice targetPrice : targetPrices) {
            if (targetPrice.getSymbol().equals(CoinId.SOLANA.getName())) {
                assertThat(targetPrice.getBuyTarget(), greaterThan(0.0));
                assertThat(targetPrice.getSellTarget(), is(1105.0));
            }
        }
    }

    @Test
    void updateTargetPrice_exception() {
        String invalidFilePath = "invalid/path/target-prices.json";

        assertThrows(
                IllegalStateException.class,
                () ->
                        targetPriceProvider.updateTargetPrice(
                                new StockSymbol("GOOG", "Google"), 160.0, 200.0, invalidFilePath));

        boolean found = fileContainsSymbol(new StockSymbol("GOOG", "Google"));
        assertThat(found, is(false));
    }

    @Test
    void addTargetPrice_ok() {
        String ticker = CoinId.DOGE.getName();
        TargetPrice targetPrice = new TargetPrice(ticker, 0.0, 0.0);

        boolean result = targetPriceProvider.addTargetPrice(targetPrice, FILE_PATH);

        assertThat(result, is(true));

        boolean found = fileContainsSymbol(CoinId.DOGE);
        assertThat(found, is(true));

        // Cleanup
        targetPriceProvider.removeSymbolFromTargetPrices(ticker, FILE_PATH);
        found = fileContainsSymbol(CoinId.DOGE);
        assertThat(found, is(false));
    }

    @Test
    void addTargetPrice_symbolAlreadyExists_nothingAdded() {
        String ticker = CoinId.SOLANA.getName();

        boolean found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));

        TargetPrice targetPrice = new TargetPrice(ticker, 0.0, 0.0);
        boolean result = targetPriceProvider.addTargetPrice(targetPrice, FILE_PATH);

        assertThat(result, is(false));

        found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));
    }

    @Test
    void addTargetPrice_exception_nothingAdded() {
        String ticker = new StockSymbol("AMZN", "Amazon").getTicker();

        // Simulate an exception by providing an invalid file path
        String invalidFilePath = "invalid/path/target-prices.json";
        TargetPrice targetPrice = new TargetPrice(ticker, 0.0, 0.0);
        boolean result = targetPriceProvider.addTargetPrice(targetPrice, invalidFilePath);

        assertThat(result, is(false));

        // Ensure the symbol was not added to the original file
        boolean found = fileContainsSymbol(new StockSymbol("AMZN", "Amazon"));
        assertThat(found, is(false));
    }

    @Test
    void removeSymbolFromTargetPrices_symbolPresent_isRemoved() {
        String ticker = CoinId.SOLANA.getName();

        boolean found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));

        targetPriceProvider.removeSymbolFromTargetPrices(ticker, FILE_PATH);

        found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(false));

        // cleanup: add it back for other tests
        TargetPrice targetPrice = new TargetPrice(ticker, 0.0, 0.0);
        targetPriceProvider.addTargetPrice(targetPrice, FILE_PATH);
    }

    @Test
    void removeSymbolFromTargetPrices_symbolNotPresent_nothingHappens() {
        String ticker = CoinId.DOGE.getName();

        boolean found = fileContainsSymbol(CoinId.DOGE);
        assertThat(found, is(false));

        targetPriceProvider.removeSymbolFromTargetPrices(ticker, FILE_PATH);

        found = fileContainsSymbol(CoinId.DOGE);
        assertThat(found, is(false));
    }

    @Test
    void removeSymbolFromTargetPrices_exception_nothingHappens() {
        String ticker = CoinId.BITCOIN.getName();

        // Simulate an exception by providing an invalid file path
        String invalidFilePath = "invalid/path/target-prices.json";
        boolean result = targetPriceProvider.removeSymbolFromTargetPrices(ticker, invalidFilePath);

        // Method returns false on error (doesn't throw)
        assertThat(result, is(false));

        // Ensure the symbol was not removed from the original file
        boolean found = fileContainsSymbol(CoinId.BITCOIN);
        assertThat(found, is(true));
    }

    @Test
    void getStockTargetPrices_ok() {
        List<TargetPrice> targetPrices = targetPriceProvider.getStockTargetPrices();

        assertThat(targetPrices, notNullValue());
    }

    @Test
    void getCoinTargetPrices_ok() {
        List<TargetPrice> targetPrices = targetPriceProvider.getCoinTargetPrices();

        assertThat(targetPrices, notNullValue());
    }
}
