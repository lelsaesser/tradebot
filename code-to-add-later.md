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

## InsiderTracker.java

```java
package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.common.StockSymbol;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class InsiderTracker {

    private final FinnhubClient finnhubClient;

    @Autowired
    public InsiderTracker(FinnhubClient finnhubClient) {
        this.finnhubClient = finnhubClient;
    }

    public void evaluateInsiderActivity() throws InterruptedException {
        List<StockSymbol> tickers = StockSymbol.getAll();
        for (StockSymbol ticker : tickers) {
            evaluateInsiderActivityForTicker(ticker);
            Thread.sleep(100);
        }
    }

    public void evaluateInsiderSentiment() throws InterruptedException {
        List<StockSymbol> tickers = StockSymbol.getAll();
        for (StockSymbol ticker : tickers) {
            evaluateInsiderSentimentForTicker(ticker);
            Thread.sleep(100);
        }
    }

    private void evaluateInsiderActivityForTicker(StockSymbol ticker) {
        InsiderTransactionResponse response = finnhubClient.getInsiderTransactions(ticker);

        if (response.data().isEmpty()) {
            return;
        }

        int sellCount = 0;

        for (InsiderTransactionResponse.Transaction transaction : response.data()) {
            if (Objects.equals(transaction.transactionCode(), "S")) {
                sellCount++;
            }
        }
        if (sellCount > 0) {
            log.info("Insider transactions found for {}: {} sells", ticker.getTicker(), sellCount);
        }
    }

    private void evaluateInsiderSentimentForTicker(StockSymbol ticker) {
        InsiderSentimentResponse response = finnhubClient.getInsiderSentiment(ticker);

        for (InsiderSentimentResponse.InsiderSentiment sentiment : response.data()) {
            log.info("{} - Insider sentiment: MSPR: {} | Change: {}",
                    sentiment.symbol(),
                    sentiment.mspr(),
                    sentiment.change()
            );
        }

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

## InsiderTransactionResponse.java

```java
package org.tradelite.client.finnhub.dto;

import java.util.List;

public record InsiderTransactionResponse(
        List<Transaction> data,
        String symbol
) {
    public record Transaction(
            String name,
            int share,
            int change,
            String filingDate,
            String transactionDate,
            String transactionCode,
            double transactionPrice
    ) {}
}
```
