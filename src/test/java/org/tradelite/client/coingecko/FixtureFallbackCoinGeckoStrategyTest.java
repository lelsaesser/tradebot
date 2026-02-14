package org.tradelite.client.coingecko;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.config.TradebotApiProperties;

class FixtureFallbackCoinGeckoStrategyTest {

    @Test
    void onPriceFailure_usesCoinSpecificFixtureWhenPresent() throws Exception {
        Path base = Files.createTempDirectory("coingecko-fixture");
        Path priceDir = base.resolve("coingecko/price");
        Files.createDirectories(priceDir);
        Files.writeString(priceDir.resolve("bitcoin.json"), "{\"usd\":456.0,\"usd_24h_change\":1.5}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackCoinGeckoStrategy strategy =
                new FixtureFallbackCoinGeckoStrategy(properties, new ObjectMapper());

        CoinGeckoPriceResponse.CoinData result =
                strategy.onPriceFailure(CoinId.BITCOIN, new RuntimeException("fail"));

        assertThat(result.getUsd(), is(456.0));
        assertThat(result.getCoinId(), is(CoinId.BITCOIN));
    }

    @Test
    void onPriceFailure_usesDefaultFixtureWhenSpecificMissing() throws Exception {
        Path base = Files.createTempDirectory("coingecko-fixture-default");
        Path priceDir = base.resolve("coingecko/price");
        Files.createDirectories(priceDir);
        Files.writeString(priceDir.resolve("default.json"), "{\"usd\":123.0,\"usd_24h_change\":0.1}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackCoinGeckoStrategy strategy =
                new FixtureFallbackCoinGeckoStrategy(properties, new ObjectMapper());

        CoinGeckoPriceResponse.CoinData result =
                strategy.onPriceFailure(CoinId.BITCOIN, new RuntimeException("fail"));

        assertThat(result.getUsd(), is(123.0));
    }

    @Test
    void onPriceFailure_missingFixtures_throws() throws Exception {
        Path base = Files.createTempDirectory("coingecko-fixture-missing");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFixtureBasePath(base.toString());
        FixtureFallbackCoinGeckoStrategy strategy =
                new FixtureFallbackCoinGeckoStrategy(properties, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> strategy.onPriceFailure(CoinId.BITCOIN, new RuntimeException("fail")));
    }
}
