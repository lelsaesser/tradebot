package org.tradelite.client.finnhub;

import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;

public interface FinnhubFallbackStrategy {
    PriceQuoteResponse onQuoteFailure(StockSymbol ticker, Exception cause);

    InsiderTransactionResponse onInsiderFailure(StockSymbol ticker, Exception cause);
}
