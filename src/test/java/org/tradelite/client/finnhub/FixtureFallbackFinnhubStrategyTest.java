package org.tradelite.client.finnhub;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.TradebotApiProperties;

class FixtureFallbackFinnhubStrategyTest {

    @Test
    void onQuoteFailure_usesSymbolSpecificFixtureWhenPresent() throws Exception {
        Path base = Files.createTempDirectory("finnhub-fixture");
        Path quoteDir = base.resolve("finnhub/quote");
        Files.createDirectories(quoteDir);
        Files.writeString(quoteDir.resolve("META.json"), "{" +
                "\"c\":120.0,\"o\":119.0,\"h\":121.0,\"l\":118.0,\"d\":1.0,\"dp\":1.0,\"pc\":119.0}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackFinnhubStrategy strategy =
                new FixtureFallbackFinnhubStrategy(properties, new ObjectMapper());

        PriceQuoteResponse result =
                strategy.onQuoteFailure(new StockSymbol("META", "Meta"), new RuntimeException("x"));

        assertThat(result.getCurrentPrice(), is(120.0));
        assertThat(result.getStockSymbol().getTicker(), is("META"));
    }

    @Test
    void onQuoteFailure_usesDefaultFixtureWhenSymbolSpecificMissing() throws Exception {
        Path base = Files.createTempDirectory("finnhub-fixture-default");
        Path quoteDir = base.resolve("finnhub/quote");
        Files.createDirectories(quoteDir);
        Files.writeString(quoteDir.resolve("default.json"), "{" +
                "\"c\":99.0,\"o\":98.0,\"h\":100.0,\"l\":97.0,\"d\":1.0,\"dp\":1.0,\"pc\":98.0}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackFinnhubStrategy strategy =
                new FixtureFallbackFinnhubStrategy(properties, new ObjectMapper());

        PriceQuoteResponse result =
                strategy.onQuoteFailure(new StockSymbol("AAPL", "Apple"), new RuntimeException("x"));

        assertThat(result.getCurrentPrice(), is(99.0));
    }

    @Test
    void onInsiderFailure_missingFixtures_throws() throws Exception {
        Path base = Files.createTempDirectory("finnhub-fixture-missing");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackFinnhubStrategy strategy =
                new FixtureFallbackFinnhubStrategy(properties, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () ->
                        strategy.onInsiderFailure(
                                new StockSymbol("META", "Meta"), new RuntimeException("fail")));
    }

    @Test
    void onInsiderFailure_usesDefaultFixture() throws Exception {
        Path base = Files.createTempDirectory("finnhub-fixture-insider");
        Path insiderDir = base.resolve("finnhub/insider");
        Files.createDirectories(insiderDir);
        Files.writeString(
                insiderDir.resolve("default.json"),
                "{\"data\":[{\"name\":\"Alice\",\"share\":1,\"change\":1,\"filingDate\":\"2025-01-01\",\"transactionDate\":\"2025-01-01\",\"transactionCode\":\"S\",\"transactionPrice\":10.0}]}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackFinnhubStrategy strategy =
                new FixtureFallbackFinnhubStrategy(properties, new ObjectMapper());

        InsiderTransactionResponse result =
                strategy.onInsiderFailure(new StockSymbol("META", "Meta"), new RuntimeException("x"));

        assertThat(result.data().size(), is(1));
    }
}
