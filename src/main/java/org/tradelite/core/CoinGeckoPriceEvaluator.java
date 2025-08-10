package org.tradelite.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.CoinId;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CoinGeckoPriceEvaluator extends BasePriceEvaluator {

    private final CoinGeckoClient coinGeckoClient;
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    protected final Map<CoinId, Double> lastPriceCache = new EnumMap<>(CoinId.class);
    protected final Map<CoinId, Double> dailyLowPrice = new ConcurrentHashMap<>();
    protected final Map<CoinId, Double> dailyHighPrice = new ConcurrentHashMap<>();


    @Autowired
    public CoinGeckoPriceEvaluator(CoinGeckoClient coinGeckoClient, TargetPriceProvider targetPriceProvider,
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
            if (priceData == null || (lastPrice != null && Math.abs(lastPrice - priceData.getUsd()) < 0.0001)) {
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
                    comparePrices(priceData.getCoinId(), priceData.getUsd(), targetPrice.getBuyTarget(), targetPrice.getSellTarget());
                }
            }
        }

        return coinData.size();

    }

    public void evaluateHighPriceChange(CoinGeckoPriceResponse.CoinData priceData) {
        CoinId coinId = priceData.getCoinId();
        double currentPrice = priceData.getUsd();

        dailyLowPrice.putIfAbsent(coinId, currentPrice);
        dailyHighPrice.putIfAbsent(coinId, currentPrice);

        if (currentPrice < dailyLowPrice.get(coinId)) {
            dailyLowPrice.put(coinId, currentPrice);
        }
        if (currentPrice > dailyHighPrice.get(coinId)) {
            dailyHighPrice.put(coinId, currentPrice);
        }

        double low = dailyLowPrice.get(coinId);
        double high = dailyHighPrice.get(coinId);
        double percentChange = ((high - low) / low) * 100;

        if ((percentChange > 5.0 || percentChange < -5.0) && !targetPriceProvider.isSymbolIgnored(coinId, IgnoreReason.CHANGE_PERCENT_ALERT)) {
            String emoji = currentPrice > lastPriceCache.get(coinId) ? "📈" : "📉";
            telegramClient.sendMessage(emoji + " High daily price swing detected for " + coinId.getId() + ": " + String.format("%.2f", percentChange) + "%");
            targetPriceProvider.addIgnoredSymbol(coinId, IgnoreReason.CHANGE_PERCENT_ALERT);
        }
    }

    public void resetDailyPrices() {
        dailyLowPrice.clear();
        dailyHighPrice.clear();
    }
}
