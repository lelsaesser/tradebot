package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.RsiService;
import org.tradelite.service.StockSymbolRegistry;

@Component
public class RemoveCommandProcessor implements TelegramCommandProcessor<RemoveCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final RsiService rsiService;

    @Autowired
    public RemoveCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            StockSymbolRegistry stockSymbolRegistry,
            RsiService rsiService) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.stockSymbolRegistry = stockSymbolRegistry;
        this.rsiService = rsiService;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof RemoveCommand;
    }

    @Override
    public void processCommand(RemoveCommand command) {
        String ticker = command.getTicker();

        // Remove from stock symbol registry
        boolean symbolRemoved = stockSymbolRegistry.removeSymbol(ticker);

        // Remove from target prices
        boolean priceRemoved = removeFromTargetPrices(ticker);

        // Remove RSI data
        boolean rsiRemoved = rsiService.removeSymbolRsiData(ticker);

        if (symbolRemoved || priceRemoved || rsiRemoved) {
            telegramClient.sendMessage(
                    "Removed "
                            + ticker
                            + " from monitoring."
                            + (rsiRemoved ? " Deleted RSI historical data." : ""));
        } else {
            telegramClient.sendMessage("Symbol " + ticker + " not found or already removed.");
        }
    }

    private boolean removeFromTargetPrices(String ticker) {
        return targetPriceProvider.removeSymbolFromTargetPrices(ticker, FILE_PATH_STOCKS);
    }
}
