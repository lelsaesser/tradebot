package org.tradelite.client.coingecko;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class CoinGeckoClient {

    private final String baseUrl;
    private final String apiKey;

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;

    @Autowired
    public CoinGeckoClient(
        RestTemplate restTemplate,
        ApiRequestMeteringService meteringService,
        @Value("${coingecko.base-url}") String baseUrl,
        @Value("${coingecko.api-key}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public CoinGeckoPriceResponse.CoinData getCoinPriceData(CoinId coinId) {
        String endpointUrl = "/simple/price";
        String url =
            baseUrl +
            endpointUrl +
            "?ids=" +
            coinId.getId() +
            "&vs_currencies=usd&include_24hr_change=true";

        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.add("x-cg-demo-api-key", apiKey);
        }
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            meteringService.incrementCoingeckoRequests();
            ResponseEntity<CoinGeckoPriceResponse> response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CoinGeckoPriceResponse.class
                );
            if (
                response.getStatusCode().is2xxSuccessful() &&
                response.getBody() != null
            ) {
                CoinGeckoPriceResponse.CoinData data = response
                    .getBody()
                    .getCoinData()
                    .get(coinId.getId());
                data.setCoinId(coinId);
                return data;
            } else {
                log.error(
                    "Failed to fetch coin price data for {}: {}",
                    coinId.getId(),
                    response.getStatusCode()
                );
                throw new IllegalStateException(
                    "Failed to fetch coin price data: " +
                        response.getStatusCode()
                );
            }
        } catch (RestClientException e) {
            log.error(
                "Error fetching coin price data for {}: {}",
                coinId.getId(),
                e.getMessage()
            );
            throw e;
        }
    }
}
