package org.tradelite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.yahoo.YahooFetchException;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.common.AssetType;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolLifecycleListener;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.NewlyAddedSymbolRepository;

/**
 * Shared service for adding and removing tracked symbols. Used by both Telegram command processors
 * and the dashboard REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolManagementService {

    private final SymbolRegistry symbolRegistry;
    private final TargetPriceProvider targetPriceProvider;
    private final FinnhubClient finnhubClient;
    private final CoinGeckoClient coinGeckoClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final NewlyAddedSymbolRepository newlyAddedSymbolRepository;
    private final java.util.List<SymbolLifecycleListener> lifecycleListeners;

    /**
     * Result of an add-symbol operation.
     *
     * @param success whether the operation succeeded
     * @param message human-readable outcome message
     */
    public record AddResult(boolean success, String message) {}

    /**
     * Validates, adds, and queues a symbol for OHLCV backfill.
     *
     * @param ticker uppercase ticker symbol
     * @param displayName human-readable name
     * @param buyTarget initial buy target (null = none)
     * @param sellTarget initial sell target (null = none)
     * @return AddResult indicating success or failure reason
     */
    public AddResult addSymbol(
            String ticker, String displayName, Double buyTarget, Double sellTarget) {
        if (!isValidTicker(ticker, displayName)) {
            String source =
                    symbolRegistry.isInternationalSymbol(ticker)
                            ? "Yahoo Finance"
                            : "Finnhub or CoinGecko";
            return new AddResult(
                    false,
                    "Invalid ticker: "
                            + ticker
                            + ". Could not fetch price data from "
                            + source
                            + ".");
        }

        boolean symbolAdded = symbolRegistry.addSymbol(ticker, displayName);
        if (!symbolAdded) {
            return new AddResult(
                    false, "Failed to add symbol: " + ticker + ". It may already exist.");
        }

        TargetPrice targetPrice =
                new TargetPrice(
                        ticker,
                        buyTarget != null ? buyTarget : 0.0,
                        sellTarget != null ? sellTarget : 0.0);
        boolean priceAdded = targetPriceProvider.addTargetPrice(targetPrice, AssetType.STOCK);
        if (!priceAdded) {
            symbolRegistry.removeSymbol(ticker);
            return new AddResult(false, "Failed to add target prices for: " + ticker);
        }

        try {
            newlyAddedSymbolRepository.insert(ticker, System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            log.error("Failed to queue {} for OHLCV backfill: {}", ticker, e.getMessage());
        }

        return new AddResult(true, "Added " + displayName + " (" + ticker + ").");
    }

    /**
     * Removes a symbol from the registry, target prices, and all per-symbol state owned by
     * registered {@link SymbolLifecycleListener} beans (RSI/RS/ROC state, OHLCV cache, accumulation
     * streaks, etc.). Listener fan-out is fail-isolated — a single listener throwing is logged and
     * does not block the others or change the return value.
     *
     * @param ticker uppercase ticker symbol
     * @return true if removed, false if not found
     */
    public boolean removeSymbol(String ticker) {
        boolean removed = symbolRegistry.removeSymbol(ticker);
        if (!removed) {
            return false;
        }
        targetPriceProvider.removeSymbolFromTargetPrices(ticker, AssetType.STOCK);
        for (SymbolLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onSymbolRemoved(ticker);
            } catch (Exception e) {
                log.warn(
                        "Lifecycle listener {} failed for ticker {}: {}",
                        listener.getClass().getSimpleName(),
                        ticker,
                        e.getMessage());
            }
        }
        return true;
    }

    public boolean isValidTicker(String ticker, String displayName) {
        if (symbolRegistry.isInternationalSymbol(ticker)) {
            return isValidInternationalTicker(ticker);
        }
        return isValidDomesticTicker(ticker, displayName);
    }

    private boolean isValidInternationalTicker(String ticker) {
        try {
            var records = yahooFinanceClient.fetchDailyOhlcv(ticker, 5);
            if (!records.isEmpty()) {
                log.info("Ticker {} validated successfully via Yahoo Finance", ticker);
                return true;
            }
        } catch (YahooFetchException e) {
            log.info("Yahoo validation failed for ticker {}: {}", ticker, e.getMessage());
        }
        log.warn("Ticker {} could not be validated via Yahoo Finance", ticker);
        return false;
    }

    private boolean isValidDomesticTicker(String ticker, String displayName) {
        // Try Finnhub first (for stocks). Use the non-throwing variant — an unknown symbol is an
        // expected outcome here, not an error worth logging loudly. See issue #481.
        StockSymbol tempStockSymbol = new StockSymbol(ticker, displayName);
        var quote = finnhubClient.tryGetPriceQuote(tempStockSymbol);
        if (quote.isPresent() && quote.get().isValid()) {
            log.info("Ticker {} validated successfully via Finnhub", ticker);
            return true;
        }

        try {
            CoinId tempCoinId =
                    CoinId.fromString(ticker.toLowerCase())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Not a known crypto coin ID"));
            CoinGeckoPriceResponse.CoinData coinData = coinGeckoClient.getCoinPriceData(tempCoinId);
            if (coinData != null && coinData.getUsd() > 0) {
                log.info("Ticker {} validated successfully via CoinGecko", ticker);
                return true;
            }
        } catch (Exception e) {
            log.info("CoinGecko validation failed for ticker {}: {}", ticker, e.getMessage());
        }

        log.warn("Ticker {} could not be validated via Finnhub or CoinGecko", ticker);
        return false;
    }
}
