package org.tradelite.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TickerSymbol;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;

@Component
@RequiredArgsConstructor
public class EvaluatorLivePriceSource implements LivePriceSource {

    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @Override
    public Optional<Double> getPrice(TickerSymbol symbol) {
        if (symbol.getSymbolType() == SymbolType.STOCK) {
            return Optional.ofNullable(
                    finnhubPriceEvaluator.getLastPriceCache().get(symbol.getName()));
        } else if (symbol.getSymbolType() == SymbolType.CRYPTO) {
            CoinId coinId = (CoinId) symbol;
            return Optional.ofNullable(
                    coinGeckoPriceEvaluator.getLastPriceCache().get(coinId));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Double> getPriceByKey(String symbolKey) {
        Double stockPrice = finnhubPriceEvaluator.getLastPriceCache().get(symbolKey);
        if (stockPrice != null) {
            return Optional.of(stockPrice);
        }
        return CoinId.fromString(symbolKey)
                .map(coinId -> coinGeckoPriceEvaluator.getLastPriceCache().get(coinId));
    }
}
