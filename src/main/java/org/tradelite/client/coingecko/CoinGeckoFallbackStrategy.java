package org.tradelite.client.coingecko;

import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;

public interface CoinGeckoFallbackStrategy {
    CoinGeckoPriceResponse.CoinData onPriceFailure(CoinId coinId, Exception cause);
}
