package org.tradelite.client.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.service.SymbolManagementService;

@Component
public class RemoveCommandProcessor implements TelegramCommandProcessor<RemoveCommand> {

    private final SymbolManagementService symbolManagementService;
    private final TelegramGateway telegramClient;

    @Autowired
    public RemoveCommandProcessor(
            SymbolManagementService symbolManagementService, TelegramGateway telegramClient) {
        this.symbolManagementService = symbolManagementService;
        this.telegramClient = telegramClient;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof RemoveCommand;
    }

    @Override
    public void processCommand(RemoveCommand command) {
        String ticker = command.getTicker();
        boolean removed = symbolManagementService.removeSymbol(ticker);
        if (removed) {
            telegramClient.sendMessage("Removed " + ticker + " from monitoring.");
        } else {
            telegramClient.sendMessage("Symbol " + ticker + " not found or already removed.");
        }
    }
}
