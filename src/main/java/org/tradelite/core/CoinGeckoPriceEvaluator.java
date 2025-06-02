package org.tradelite.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.CoinId;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.ArrayList;
import java.util.List;

@Component
public class CoinGeckoPriceEvaluator extends BasePriceEvaluator {

    private final CoinGeckoClient coinGeckoClient;
    private final TargetPriceProvider targetPriceProvider;

    @Autowired
    public CoinGeckoPriceEvaluator(CoinGeckoClient coinGeckoClient, TargetPriceProvider targetPriceProvider,
                                   TelegramClient telegramClient) {
        super(telegramClient, targetPriceProvider);
        this.coinGeckoClient = coinGeckoClient;
        this.targetPriceProvider = targetPriceProvider;
    }

    @Override
    public void evaluatePrice() throws InterruptedException {

        List<CoinId> coinIds = CoinId.getAll();
        List<CoinGeckoPriceResponse.CoinData> coinData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getCoinTargetPrices();

        for (CoinId coinId : coinIds) {
            CoinGeckoPriceResponse.CoinData priceData = coinGeckoClient.getCoinPriceData(coinId);
            coinData.add(priceData);
            Thread.sleep(100);
        }

        for (CoinGeckoPriceResponse.CoinData priceData : coinData) {
            for (TargetPrice targetPrice : targetPrices) {
                if (priceData.getCoinId().getId().equals(targetPrice.getSymbol())) {
                    comparePrices(priceData.getCoinId(), priceData.getUsd(), targetPrice.getBuyTarget(), targetPrice.getSellTarget());
                }
            }
        }

    }
}
