package org.tradelite.client.finnhub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;

@Slf4j
@Component
@Profile("!dev")
public class NoFallbackFinnhubStrategy implements FinnhubFallbackStrategy {

    @Override
    public PriceQuoteResponse onQuoteFailure(StockSymbol ticker, Exception cause) {
        log.error("Failed to fetch Finnhub quote for {}", ticker.getTicker(), cause);
        throw toRuntime(cause);
    }

    @Override
    public InsiderTransactionResponse onInsiderFailure(StockSymbol ticker, Exception cause) {
        log.error("Failed to fetch Finnhub insider transactions for {}", ticker.getTicker(), cause);
        throw toRuntime(cause);
    }

    private RuntimeException toRuntime(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
