package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@Component
public class AddCommandProcessor implements TelegramCommandProcessor<AddCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;

    @Autowired
    public AddCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramClient telegramClient,
            StockSymbolRegistry stockSymbolRegistry) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.stockSymbolRegistry = stockSymbolRegistry;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof AddCommand;
    }

    @Override
    public void processCommand(AddCommand command) {
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
}
