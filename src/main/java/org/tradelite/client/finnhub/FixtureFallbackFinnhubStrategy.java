package org.tradelite.client.finnhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.TradebotApiProperties;

@Slf4j
@Component
@Profile("dev")
public class FixtureFallbackFinnhubStrategy implements FinnhubFallbackStrategy {

    private final TradebotApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public FixtureFallbackFinnhubStrategy(
            TradebotApiProperties apiProperties, ObjectMapper objectMapper) {
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PriceQuoteResponse onQuoteFailure(StockSymbol ticker, Exception cause) {
        return loadFixture(
                "finnhub/quote",
                ticker.getTicker().toUpperCase(),
                PriceQuoteResponse.class,
                cause,
                fixture -> {
                    fixture.setStockSymbol(ticker);
                    return fixture;
                });
    }

    @Override
    public InsiderTransactionResponse onInsiderFailure(StockSymbol ticker, Exception cause) {
        return loadFixture(
                "finnhub/insider",
                ticker.getTicker().toUpperCase(),
                InsiderTransactionResponse.class,
                cause,
                fixture -> fixture);
    }

    private <T> T loadFixture(
            String subPath,
            String symbolOrId,
            Class<T> type,
            Exception cause,
            Function<T, T> mapper) {
        Path symbolPath = Path.of(apiProperties.getFixtureBasePath(), subPath, symbolOrId + ".json");
        Path defaultPath = Path.of(apiProperties.getFixtureBasePath(), subPath, "default.json");
        Path selectedPath = Files.exists(symbolPath) ? symbolPath : defaultPath;

        try {
            T loaded = objectMapper.readValue(selectedPath.toFile(), type);
            if (!Files.exists(symbolPath)) {
                log.warn(
                        "Falling back to default fixture {} for {} after API failure: {}",
                        selectedPath,
                        symbolOrId,
                        cause.getMessage());
            } else {
                log.warn(
                        "Using fixture {} for {} after API failure: {}",
                        selectedPath,
                        symbolOrId,
                        cause.getMessage());
            }
            return mapper.apply(loaded);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed API call and fixture lookup for " + symbolOrId + " in " + selectedPath,
                    e);
        }
    }
}
