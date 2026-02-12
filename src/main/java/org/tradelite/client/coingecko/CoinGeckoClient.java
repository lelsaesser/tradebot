package org.tradelite.client.coingecko;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class CoinGeckoClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public CoinGeckoClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    public CoinGeckoPriceResponse.CoinData getCoinPriceData(CoinId coinId) {
        if (apiProperties.getCoingeckoKey() == null || apiProperties.getCoingeckoKey().isBlank()) {
            return fallbackCoinData(coinId, new IllegalStateException("COINGECKO key not configured"));
        }

        String endpointUrl = "/simple/price";
        String url =
                BASE_URL
                        + endpointUrl
                        + "?ids="
                        + coinId.getId()
                        + "&vs_currencies=usd"
                        + "&include_24hr_change=true";

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-cg-demo-api-key", apiProperties.getCoingeckoKey());
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            meteringService.incrementCoingeckoRequests();
            ResponseEntity<CoinGeckoPriceResponse> response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, entity, CoinGeckoPriceResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CoinGeckoPriceResponse.CoinData data =
                        response.getBody().getCoinData().get(coinId.getId());
                if (data == null) {
                    return fallbackCoinData(
                            coinId,
                            new IllegalStateException(
                                    "CoinGecko response missing payload for " + coinId.getId()));
                }
                data.setCoinId(coinId);
                return data;
            } else {
                return fallbackCoinData(
                        coinId,
                        new IllegalStateException(
                                "Failed to fetch coin price data: " + response.getStatusCode()));
            }
        } catch (RestClientException e) {
            return fallbackCoinData(coinId, e);
        }
    }

    private CoinGeckoPriceResponse.CoinData fallbackCoinData(CoinId coinId, Exception original) {
        if (!apiProperties.isFixtureFallbackEnabled()) {
            log.error("Error fetching coin price data for {}: {}", coinId.getId(), original.getMessage());
            throw unwrapRuntimeException(original);
        }

        Path coinPath = Path.of(apiProperties.getFixtureBasePath(), "coingecko/price", coinId.getId() + ".json");
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
                        original.getMessage());
            } else {
                log.warn(
                        "Using CoinGecko fixture {} for {} after API failure: {}",
                        selectedPath,
                        coinId.getId(),
                        original.getMessage());
            }
            return fixture;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed CoinGecko API call and fixture lookup for " + coinId.getId(), e);
        }
    }

    private RuntimeException unwrapRuntimeException(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
