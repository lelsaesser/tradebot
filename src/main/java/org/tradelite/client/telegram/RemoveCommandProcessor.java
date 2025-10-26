package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_COINS;
import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TargetPriceProvider;

@Component
public class RemoveCommandProcessor implements TelegramCommandProcessor<RemoveCommand> {

    private final TargetPriceProvider targetPriceProvider;

    @Autowired
    public RemoveCommandProcessor(TargetPriceProvider targetPriceProvider) {
        this.targetPriceProvider = targetPriceProvider;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof RemoveCommand;
    }

    @Override
    public void processCommand(RemoveCommand command) {
        String filePath =
                command.getSymbolType() == SymbolType.STOCK ? FILE_PATH_STOCKS : FILE_PATH_COINS;
        targetPriceProvider.removeSymbolFromTargetPriceConfig(command, filePath);
    }
}
