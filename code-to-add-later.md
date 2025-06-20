## FinnhubClient.java

```java
    public void getFinancialData(StockSymbol ticker) {
        String baseUrl = "/stock/metric?symbol=%s&metric=all";
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            log.info(response.getBody());
        } catch (Exception e) {
            log.error("Error fetching financial data: {}", e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
```

```
    public InsiderSentimentResponse getInsiderSentiment(StockSymbol ticker) {
        String fromDate = DateUtil.getDateTwoMonthsAgo(null);
        String baseUrl = "/stock/insider-sentiment?symbol=%s&from=" + fromDate;
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<InsiderSentimentResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, InsiderSentimentResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
```

## InsiderSentimentResponse.java

```java
package org.tradelite.client.finnhub.dto;

import java.util.List;


public record InsiderSentimentResponse (
        List<InsiderSentiment> data,
        String symbol
) {
    public record InsiderSentiment(
            int change,
            int month,
            int year,
            String symbol,
            double mspr
    ) {}
}
```
