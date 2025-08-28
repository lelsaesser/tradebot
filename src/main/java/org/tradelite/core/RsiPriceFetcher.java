package org.tradelite.core;

import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RsiPriceFetcher {

    private final FinnhubClient finnhubClient;
    private final CoinGeckoClient coinGeckoClient;
    private final TargetPriceProvider targetPriceProvider;
    private final RsiService rsiService;

    public void fetchStockClosingPrices() {
        LocalDate today = LocalDate.now();

        for (TargetPrice targetPrice : targetPriceProvider.getStockTargetPrices()) {
            Optional<StockSymbol> stockSymbol = StockSymbol.fromString(targetPrice.getSymbol());
            if (stockSymbol.isPresent()) {
                PriceQuoteResponse priceQuote = finnhubClient.getPriceQuote(stockSymbol.get());
                rsiService.addPrice(stockSymbol.get(), priceQuote.getCurrentPrice(), today);
            }
        }
    }

    public void fetchCryptoClosingPrices() {
        LocalDate today = LocalDate.now();

        for (TargetPrice targetPrice : targetPriceProvider.getCoinTargetPrices()) {
            Optional<CoinId> coinId = CoinId.fromString(targetPrice.getSymbol());
            if (coinId.isPresent()) {
                CoinGeckoPriceResponse.CoinData coinData = coinGeckoClient.getCoinPriceData(coinId.get());
                rsiService.addPrice(coinId.get(), coinData.getUsd(), today);
            }
        }
    }
}
