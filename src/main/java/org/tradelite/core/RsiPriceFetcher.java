package org.tradelite.core;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.RsiService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RsiPriceFetcher {

    private final FinnhubClient finnhubClient;
    private final CoinGeckoClient coinGeckoClient;
    private final TargetPriceProvider targetPriceProvider;
    private final RsiService rsiService;

    public void fetchStockClosingPrices() throws IOException {
        LocalDate today = LocalDate.now();

        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            try {
                Optional<StockSymbol> stockSymbol = StockSymbol.fromString(targetPrice.getSymbol());
                if (stockSymbol.isPresent()) {
                    PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(stockSymbol.get());
                    rsiService.addPrice(stockSymbol.get(), priceQuote.getCurrentPrice(), today);
                }
            } catch (Exception e) {
                log.error("Error fetching stock price for RSI", e);
                throw e;
            }
        }
    }

    public void fetchCryptoClosingPrices() throws IOException {
        LocalDate today = LocalDate.now();

        for (TargetPrice targetPrice : targetPriceProvider.getCoinTargetPrices()) {
            try {
                Optional<CoinId> coinId = CoinId.fromString(targetPrice.getSymbol());
                if (coinId.isPresent()) {
                    CoinGeckoPriceResponse.CoinData coinData =
                            coinGeckoClient.getCoinPriceData(coinId.get());
                    rsiService.addPrice(coinId.get(), coinData.getUsd(), today);
                }
            } catch (Exception e) {
                log.error("Error fetching crypto price for RSI", e);
                throw e;
            }
        }
    }
}
