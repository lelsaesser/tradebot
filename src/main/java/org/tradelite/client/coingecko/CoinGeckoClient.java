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
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class CoinGeckoClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final String API_KEY = System.getenv("COINGECKO_API_KEY");

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;

    @Autowired
    public CoinGeckoClient(RestTemplate restTemplate, ApiRequestMeteringService meteringService) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
    }

    public CoinGeckoPriceResponse.CoinData getCoinPriceData(CoinId coinId) {
        String endpointUrl = "/simple/price";
        String url = BASE_URL + endpointUrl + "?ids=" + coinId.getId() + "&vs_currencies=usd" + "&include_24hr_change=true";

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-cg-demo-api-key", API_KEY);
        headers.set("Accept", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            meteringService.incrementCoingeckoRequests();
            ResponseEntity<CoinGeckoPriceResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, CoinGeckoPriceResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CoinGeckoPriceResponse.CoinData data = response.getBody().getCoinData().get(coinId.getId());
                data.setCoinId(coinId);
                return data;
            } else {
                log.error("Failed to fetch coin price data for {}: {}", coinId.getId(), response.getStatusCode());
                throw new IllegalStateException("Failed to fetch coin price data: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Error fetching coin price data for {}: {}", coinId.getId(), e.getMessage());
            throw e;
        }

    }
}
