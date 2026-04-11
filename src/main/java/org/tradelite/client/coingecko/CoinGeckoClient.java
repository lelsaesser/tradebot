package org.tradelite.client.coingecko;

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

    @Autowired
    public CoinGeckoClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
    }

    public CoinGeckoPriceResponse.CoinData getCoinPriceData(CoinId coinId) {
        if (apiProperties.getCoingeckoKey() == null || apiProperties.getCoingeckoKey().isBlank()) {
            throw priceFailure(coinId, new IllegalStateException("COINGECKO key not configured"));
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

        ResponseEntity<CoinGeckoPriceResponse> response;
        try {
            meteringService.incrementCoingeckoRequests();
            response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, entity, CoinGeckoPriceResponse.class);
        } catch (RestClientException e) {
            throw priceFailure(coinId, e);
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw priceFailure(
                    coinId,
                    new IllegalStateException(
                            "Failed to fetch coin price data: " + response.getStatusCode()));
        }

        CoinGeckoPriceResponse.CoinData data = response.getBody().getCoinData().get(coinId.getId());
        if (data == null) {
            throw priceFailure(
                    coinId,
                    new IllegalStateException(
                            "CoinGecko response missing payload for " + coinId.getId()));
        }
        data.setCoinId(coinId);
        return data;
    }

    private RuntimeException priceFailure(CoinId coinId, Exception cause) {
        log.error("Failed to fetch CoinGecko price data for {}", coinId.getId(), cause);
        return toRuntime(cause);
    }

    private RuntimeException toRuntime(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
