package org.tradelite.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.AddCommand;
import org.tradelite.core.IgnoreReason;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    void updateTargetPrice() {
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
    void addSymbolToTargetPriceConfig_ok() {
        AddCommand command = new AddCommand(CoinId.POLKADOT, 160.0, 200.0, SymbolType.CRYPTO);

        boolean result = targetPriceProvider.addSymbolToTargetPriceConfig(command, FILE_PATH);

        assertThat(result, is(true));

        boolean found = fileContainsSymbol(CoinId.POLKADOT);
        assertThat(found, is(true));

        // Cleanup
        targetPriceProvider.removeSymbolFromTargetPriceConfig(CoinId.POLKADOT, FILE_PATH);
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
}
