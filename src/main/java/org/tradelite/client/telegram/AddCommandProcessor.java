package org.tradelite.client.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.service.SymbolManagementService;

@Component
public class AddCommandProcessor implements TelegramCommandProcessor<AddCommand> {

    private final SymbolManagementService symbolManagementService;
    private final TelegramGateway telegramClient;

    @Autowired
    public AddCommandProcessor(SymbolManagementService symbolManagementService, TelegramGateway telegramClient) {
        this.symbolManagementService = symbolManagementService;
        this.telegramClient = telegramClient;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof AddCommand;
    }

    @Override
    public void processCommand(AddCommand command) {
        SymbolManagementService.AddResult result = symbolManagementService.addSymbol(
                command.getTicker(),
                command.getDisplayName(),
                command.getBuyTargetPrice(),
                command.getSellTargetPrice());

        if (!result.success()) {
            telegramClient.sendMessage(result.message());
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
}
