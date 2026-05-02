package org.tradelite.client.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.AssetType;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;

@Component
public class RemoveCommandProcessor implements TelegramCommandProcessor<RemoveCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;

    @Autowired
    public RemoveCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            SymbolRegistry symbolRegistry) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.symbolRegistry = symbolRegistry;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof RemoveCommand;
    }

    @Override
    public void processCommand(RemoveCommand command) {
        String ticker = command.getTicker();

        // Remove from stock symbol registry
        boolean symbolRemoved = symbolRegistry.removeSymbol(ticker);

        // Remove from target prices
        boolean priceRemoved = removeFromTargetPrices(ticker);

        if (symbolRemoved || priceRemoved) {
            telegramClient.sendMessage("Removed " + ticker + " from monitoring.");
        } else {
            telegramClient.sendMessage("Symbol " + ticker + " not found or already removed.");
        }
    }

    private boolean removeFromTargetPrices(String ticker) {
        return targetPriceProvider.removeSymbolFromTargetPrices(ticker, AssetType.STOCK);
    }
}
