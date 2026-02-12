package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@Slf4j
@Component
public class AddCommandProcessor implements TelegramCommandProcessor<AddCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final FinnhubClient finnhubClient;
    private final CoinGeckoClient coinGeckoClient;

    @Autowired
    public AddCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            StockSymbolRegistry stockSymbolRegistry,
            FinnhubClient finnhubClient,
            CoinGeckoClient coinGeckoClient) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.stockSymbolRegistry = stockSymbolRegistry;
        this.finnhubClient = finnhubClient;
        this.coinGeckoClient = coinGeckoClient;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof AddCommand;
    }

    @Override
    public void processCommand(AddCommand command) {
        // Validate ticker by checking if price data is available
        if (!isValidTicker(command.getTicker(), command.getDisplayName())) {
            telegramClient.sendMessage(
                    "Invalid ticker symbol: "
                            + command.getTicker()
                            + ". Could not fetch price data from Finnhub or CoinGecko.");
            return;
        }

        // Add to stock symbol registry
        boolean symbolAdded =
                stockSymbolRegistry.addSymbol(command.getTicker(), command.getDisplayName());
        if (!symbolAdded) {
            telegramClient.sendMessage(
                    "Failed to add symbol: "
                            + command.getTicker()
                            + ". It may already exist or there was an error.");
            return;
        }

        // Add to target prices
        boolean priceAdded = addToTargetPrices(command);
        if (!priceAdded) {
            // Rollback symbol addition if price addition fails
            stockSymbolRegistry.removeSymbol(command.getTicker());
            telegramClient.sendMessage(
                    "Failed to add symbol to target prices: " + command.getTicker());
            return;
        }

        telegramClient.sendMessage(
                "All set!\n"
                        + "Added "
                        + command.getDisplayName()
                        + " ("
                        + command.getTicker()
                        + ") with buy target "
                        + command.getBuyTargetPrice()
                        + " and sell target "
                        + command.getSellTargetPrice()
                        + ".");
    }

    private boolean addToTargetPrices(AddCommand command) {
        TargetPrice targetPrice =
                new TargetPrice(
                        command.getTicker(),
                        command.getBuyTargetPrice(),
                        command.getSellTargetPrice());

        return targetPriceProvider.addTargetPrice(targetPrice, FILE_PATH_STOCKS);
    }

    /**
     * Validates if a ticker is valid by attempting to fetch price data from Finnhub first (for
     * stocks), then from CoinGecko (for crypto) if Finnhub fails.
     *
     * @param ticker The ticker symbol to validate
     * @param displayName The display name for the ticker
     * @return true if price data can be fetched from either source, false otherwise
     */
    private boolean isValidTicker(String ticker, String displayName) {
        // Try Finnhub first (for stocks)
        try {
            StockSymbol tempStockSymbol = new StockSymbol(ticker, displayName);
            PriceQuoteResponse quoteResponse = finnhubClient.getPriceQuote(tempStockSymbol);
            if (quoteResponse != null && quoteResponse.isValid()) {
                log.info("Ticker {} validated successfully via Finnhub", ticker);
                return true;
            }
        } catch (Exception e) {
            log.info("Finnhub validation failed for ticker {}: {}", ticker, e.getMessage());
        }

        // Try CoinGecko (for crypto)
        try {
            // CoinGecko uses lowercase IDs, so convert ticker to lowercase for the API call
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
