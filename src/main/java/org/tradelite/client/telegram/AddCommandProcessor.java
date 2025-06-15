package org.tradelite.client.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TargetPriceProvider;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_COINS;
import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

@Component
public class AddCommandProcessor implements TelegramCommandProcessor<AddCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    @Autowired
    public AddCommandProcessor(TargetPriceProvider targetPriceProvider, TelegramClient telegramClient) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof AddCommand;
    }

    @Override
    public void processCommand(AddCommand command) {
        String filePath = command.getSymbolType() == SymbolType.STOCK ? FILE_PATH_STOCKS : FILE_PATH_COINS;

        boolean success = targetPriceProvider.addSymbolToTargetPriceConfig(command, filePath);
        if (success) {
            telegramClient.sendMessage("All set!\n" +
                    "Added " + command.getSymbol().getName() + " with buy target " + command.getBuyTargetPrice() +
                    " and sell target " + command.getSellTargetPrice() + ".");
        } else {
            telegramClient.sendMessage("Failed to add symbol: " + command.getSymbol().getName() +
                    ". It may already exist or there was an error.");
        }
    }

}
