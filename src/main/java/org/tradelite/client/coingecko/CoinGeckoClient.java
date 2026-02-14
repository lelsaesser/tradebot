package org.tradelite.client.coingecko;

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

@Component
public class CoinGeckoClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;
    private final CoinGeckoFallbackStrategy fallbackStrategy;

    @Autowired
    public CoinGeckoClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties,
            CoinGeckoFallbackStrategy fallbackStrategy) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
        this.fallbackStrategy = fallbackStrategy;
    }

    public CoinGeckoPriceResponse.CoinData getCoinPriceData(CoinId coinId) {
        if (apiProperties.getCoingeckoKey() == null || apiProperties.getCoingeckoKey().isBlank()) {
            return fallbackStrategy.onPriceFailure(
                    coinId, new IllegalStateException("COINGECKO key not configured"));
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
                    return fallbackStrategy.onPriceFailure(
                            coinId,
                            new IllegalStateException(
                                    "CoinGecko response missing payload for " + coinId.getId()));
                }
                data.setCoinId(coinId);
                return data;
            } else {
                return fallbackStrategy.onPriceFailure(
                        coinId,
                        new IllegalStateException(
                                "Failed to fetch coin price data: " + response.getStatusCode()));
            }
        } catch (RestClientException e) {
            return fallbackStrategy.onPriceFailure(coinId, e);
        }
    }
}
