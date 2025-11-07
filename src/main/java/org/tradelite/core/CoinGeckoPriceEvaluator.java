package org.tradelite.core;

import java.util.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.CoinId;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@Component
public class CoinGeckoPriceEvaluator extends BasePriceEvaluator {

    private final CoinGeckoClient coinGeckoClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    @Getter
    protected final Map<CoinId, Double> lastPriceCache = new EnumMap<>(CoinId.class);

    @Autowired
    public CoinGeckoPriceEvaluator(
            CoinGeckoClient coinGeckoClient,
            TargetPriceProvider targetPriceProvider,
            TelegramClient telegramClient) {
        super(telegramClient, targetPriceProvider);
        this.coinGeckoClient = coinGeckoClient;
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
    }

    @Override
    public int evaluatePrice() throws InterruptedException {

        List<CoinId> coinIds = CoinId.getAll();
        List<CoinGeckoPriceResponse.CoinData> coinData = new ArrayList<>();
        List<TargetPrice> targetPrices = targetPriceProvider.getCoinTargetPrices();

        for (CoinId coinId : coinIds) {
            CoinGeckoPriceResponse.CoinData priceData = coinGeckoClient.getCoinPriceData(coinId);

            Double lastPrice = lastPriceCache.get(coinId);
            if (priceData == null
                    || (lastPrice != null && Math.abs(lastPrice - priceData.getUsd()) < 0.0001)) {
                continue;
            }
            lastPriceCache.put(coinId, priceData.getUsd());

            coinData.add(priceData);
            Thread.sleep(100);
        }

        for (CoinGeckoPriceResponse.CoinData priceData : coinData) {
            evaluateHighPriceChange(priceData);
            for (TargetPrice targetPrice : targetPrices) {
                if (priceData.getCoinId().getId().equalsIgnoreCase(targetPrice.getSymbol())) {
                    comparePrices(
                            priceData.getCoinId(),
                            priceData.getUsd(),
                            targetPrice.getBuyTarget(),
                            targetPrice.getSellTarget());
                }
            }
        }

        return coinData.size();
    }

    public void evaluateHighPriceChange(CoinGeckoPriceResponse.CoinData priceData) {
        CoinId coinId = priceData.getCoinId();
        double percentChange = priceData.getUsd_24h_change();
        double absPercentChange = Math.abs(percentChange);

        if (absPercentChange < 5.0) {
            return;
        }

        int alertThreshold = (int) (absPercentChange / 5.0) * 5;

        if (alertThreshold > 0
                && !targetPriceProvider.isSymbolIgnored(
                        coinId, IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold)) {
            String emoji = percentChange > 0 ? "📈" : "📉";
            telegramClient.sendMessage(
                    emoji
                            + " High daily price swing detected for "
                            + coinId.getId()
                            + ": "
                            + String.format("%.2f", percentChange)
                            + "%");
            targetPriceProvider.addIgnoredSymbol(
                    coinId, IgnoreReason.CHANGE_PERCENT_ALERT, alertThreshold);
        }
    }
}
