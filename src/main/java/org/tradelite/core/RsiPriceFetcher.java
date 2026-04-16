package org.tradelite.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RsiPriceFetcher {

    /**
     * @deprecated RsiService no longer accumulates prices via addPrice(). Stock closing prices are
     *     now sourced from OHLCV data via DailyPriceProvider. This method is retained temporarily
     *     for scheduler compatibility and will be removed in a follow-up subtask.
     */
    @Deprecated
    public void fetchStockClosingPrices() {
        log.info(
                "fetchStockClosingPrices() is a no-op: RsiService now reads from DailyPriceProvider");
    }

    /**
     * @deprecated RsiService no longer accumulates prices via addPrice(). Crypto OHLCV support is
     *     deferred. This method is retained temporarily for scheduler compatibility and will be
     *     removed in a follow-up subtask.
     */
    @Deprecated
    public void fetchCryptoClosingPrices() {
        log.info(
                "fetchCryptoClosingPrices() is a no-op: crypto RSI is deferred pending crypto OHLCV provider");
    }
}
