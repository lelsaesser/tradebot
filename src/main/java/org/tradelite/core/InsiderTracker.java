package org.tradelite.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.FinnhubClient;
import org.tradelite.client.dto.InsiderTransactionResponse;
import org.tradelite.common.StockTicker;

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

    public void evaluateInsiderActivity() {
        List<StockTicker> tickers = StockTicker.getAll();
        for (StockTicker ticker : tickers) {
            evaluateInsiderActivityForTicker(ticker);
        }
    }

    private void evaluateInsiderActivityForTicker(StockTicker ticker) {
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
}
