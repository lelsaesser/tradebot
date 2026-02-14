package org.tradelite.client.coingecko;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;

@Slf4j
@Component
@Profile("!dev")
public class NoFallbackCoinGeckoStrategy implements CoinGeckoFallbackStrategy {

    @Override
    public CoinGeckoPriceResponse.CoinData onPriceFailure(CoinId coinId, Exception cause) {
        log.error("Failed to fetch CoinGecko price data for {}", coinId.getId(), cause);
        throw toRuntime(cause);
    }

    private RuntimeException toRuntime(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
