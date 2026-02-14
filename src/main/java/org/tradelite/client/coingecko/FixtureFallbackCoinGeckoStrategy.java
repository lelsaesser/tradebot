package org.tradelite.client.coingecko;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.config.TradebotApiProperties;

@Slf4j
@Component
@Profile("dev")
public class FixtureFallbackCoinGeckoStrategy implements CoinGeckoFallbackStrategy {

    private final TradebotApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public FixtureFallbackCoinGeckoStrategy(
            TradebotApiProperties apiProperties, ObjectMapper objectMapper) {
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CoinGeckoPriceResponse.CoinData onPriceFailure(CoinId coinId, Exception cause) {
        Path coinPath =
                Path.of(apiProperties.getFixtureBasePath(), "coingecko/price", coinId.getId() + ".json");
        Path defaultPath = Path.of(apiProperties.getFixtureBasePath(), "coingecko/price", "default.json");
        Path selectedPath = Files.exists(coinPath) ? coinPath : defaultPath;

        try {
            CoinGeckoPriceResponse.CoinData fixture =
                    objectMapper.readValue(selectedPath.toFile(), CoinGeckoPriceResponse.CoinData.class);
            fixture.setCoinId(coinId);

            if (!Files.exists(coinPath)) {
                log.warn(
                        "Falling back to default CoinGecko fixture {} for {} after API failure: {}",
                        selectedPath,
                        coinId.getId(),
                        cause.getMessage());
            } else {
                log.warn(
                        "Using CoinGecko fixture {} for {} after API failure: {}",
                        selectedPath,
                        coinId.getId(),
                        cause.getMessage());
            }
            return fixture;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed CoinGecko API call and fixture lookup for " + coinId.getId(), e);
        }
    }
}
