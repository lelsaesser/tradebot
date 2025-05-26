package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderSentimentResponse;
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
