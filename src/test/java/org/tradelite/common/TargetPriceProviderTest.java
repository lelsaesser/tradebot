package org.tradelite.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.AddCommand;
import org.tradelite.client.telegram.RemoveCommand;
import org.tradelite.core.IgnoreReason;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));

        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));
        assertThat(targetPriceProvider.ignoredSymbols.get(CoinId.SOLANA.getName()).getIgnoreReasons(), aMapWithSize(2));
        assertThat(targetPriceProvider.ignoredSymbols.get(StockSymbol.AMZN.getName()).getIgnoreReasons(), aMapWithSize(1));

        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.BUY_ALERT);
        assertThat(targetPriceProvider.ignoredSymbols.get(StockSymbol.AMZN.getName()).getIgnoreReasons(), aMapWithSize(2));
    }

    @Test
    void isSymbolIgnored() {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.BUY_ALERT), is(true));
        assertThat(targetPriceProvider.isSymbolIgnored(CoinId.SOLANA, IgnoreReason.SELL_ALERT), is(false));
        assertThat(targetPriceProvider.isSymbolIgnored(StockSymbol.AMZN, IgnoreReason.SELL_ALERT), is(true));
        assertThat(targetPriceProvider.isSymbolIgnored(StockSymbol.AMZN, IgnoreReason.BUY_ALERT), is(false));
        assertThat(targetPriceProvider.isSymbolIgnored(StockSymbol.AMD, IgnoreReason.BUY_ALERT), is(false));
        assertThat(targetPriceProvider.isSymbolIgnored(CoinId.BITCOIN, IgnoreReason.SELL_ALERT), is(false));
    }

    @Test
    @SuppressWarnings("java:S2925") // Sleep is used to test the cleanup functionality
    void cleanupIgnoreSymbols() throws InterruptedException {
        targetPriceProvider.addIgnoredSymbol(CoinId.SOLANA, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.AMZN, IgnoreReason.SELL_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(2));
        Thread.sleep(3000);
        targetPriceProvider.cleanupIgnoreSymbols(1L);
        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(0));

        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.BUY_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.META, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.PLTR, IgnoreReason.BUY_ALERT);

        assertThat(targetPriceProvider.ignoredSymbols, aMapWithSize(3));
        Thread.sleep(3500);
        targetPriceProvider.addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.SELL_ALERT);
        targetPriceProvider.addIgnoredSymbol(StockSymbol.META, IgnoreReason.BUY_ALERT);
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
    void updateTargetPrice_exception() {
        String invalidFilePath = "invalid/path/target-prices.json";

        assertThrows(IllegalStateException.class, () -> targetPriceProvider.updateTargetPrice(StockSymbol.UNH, 160.0, 200.0, invalidFilePath));

        boolean found = fileContainsSymbol(StockSymbol.UNH);
        assertThat(found, is(false));
    }

    @Test
    void addSymbolToTargetPriceConfig_ok() {
        AddCommand command = new AddCommand(CoinId.POLKADOT, 160.0, 200.0, SymbolType.CRYPTO);

        boolean result = targetPriceProvider.addSymbolToTargetPriceConfig(command, FILE_PATH);

        assertThat(result, is(true));

        boolean found = fileContainsSymbol(CoinId.POLKADOT);
        assertThat(found, is(true));

        // Cleanup
        targetPriceProvider.removeSymbolFromTargetPriceConfig(new RemoveCommand(CoinId.POLKADOT, SymbolType.CRYPTO), FILE_PATH);
        found = fileContainsSymbol(CoinId.POLKADOT);
        assertThat(found, is(false));
    }

    @Test
    void addSymbolToTargetPriceConfig_symbolAlreadyExists_nothingAdded() {
        AddCommand command = new AddCommand(CoinId.SOLANA, 160.0, 200.0, SymbolType.CRYPTO);

        boolean found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));

        boolean result = targetPriceProvider.addSymbolToTargetPriceConfig(command, FILE_PATH);

        assertThat(result, is(false));

        found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));
    }

    @Test
    void addSymbolToTargetPriceConfig_exception_nothingAdded() {
        AddCommand command = new AddCommand(StockSymbol.AMZN, 160.0, 200.0, SymbolType.STOCK);

        // Simulate an exception by providing an invalid file path
        String invalidFilePath = "invalid/path/target-prices.json";
        boolean result = targetPriceProvider.addSymbolToTargetPriceConfig(command, invalidFilePath);

        assertThat(result, is(false));

        // Ensure the symbol was not added to the original file
        boolean found = fileContainsSymbol(StockSymbol.AMZN);
        assertThat(found, is(false));
    }

    @Test
    void removeSymbolFromTargetPriceConfig_symbolPresent_isRemoved() {
        RemoveCommand command = new RemoveCommand(CoinId.SOLANA, SymbolType.CRYPTO);

        boolean found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(true));

        targetPriceProvider.removeSymbolFromTargetPriceConfig(command, FILE_PATH);

        found = fileContainsSymbol(CoinId.SOLANA);
        assertThat(found, is(false));

        // cleanup: add it back for other tests
        AddCommand addCommand = new AddCommand(CoinId.SOLANA, 160.0, 200.0, SymbolType.CRYPTO);
        targetPriceProvider.addSymbolToTargetPriceConfig(addCommand, FILE_PATH);
    }

    @Test
    void removeSymbolFromTargetPriceConfig_symbolNotPresent_nothingHappens() {
        RemoveCommand command = new RemoveCommand(CoinId.POLKADOT, SymbolType.CRYPTO);

        boolean found = fileContainsSymbol(CoinId.POLKADOT);
        assertThat(found, is(false));

        targetPriceProvider.removeSymbolFromTargetPriceConfig(command, FILE_PATH);

        found = fileContainsSymbol(CoinId.POLKADOT);
        assertThat(found, is(false));
    }

    @Test
    void removeSymbolFromTargetPriceConfig_exception_nothingHappens() {
        RemoveCommand command = new RemoveCommand(CoinId.BITCOIN, SymbolType.CRYPTO);

        // Simulate an exception by providing an invalid file path
        String invalidFilePath = "invalid/path/target-prices.json";
        assertThrows(IllegalStateException.class, () -> targetPriceProvider.removeSymbolFromTargetPriceConfig(command, invalidFilePath));

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
