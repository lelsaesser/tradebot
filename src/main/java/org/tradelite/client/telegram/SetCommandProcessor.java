package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_COINS;
import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPriceProvider;

@Component
public class SetCommandProcessor implements TelegramCommandProcessor<SetCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;

    @Autowired
    public SetCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            SymbolRegistry symbolRegistry) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.symbolRegistry = symbolRegistry;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof SetCommand;
    }

    @Override
    public void processCommand(SetCommand command) {
        Double sellTarget = null;
        Double buyTarget = null;

        if ("buy".equalsIgnoreCase(command.getSubCommand())) {
            buyTarget = command.getTarget();
        } else if ("sell".equalsIgnoreCase(command.getSubCommand())) {
            sellTarget = command.getTarget();
        } else {
            throw new IllegalArgumentException("Invalid sub-command: " + command.getSubCommand());
        }

        String symbol = command.getSymbol();
        Optional<CoinId> coinId = CoinId.fromString(symbol);
        Optional<StockSymbol> stockSymbol = symbolRegistry.fromString(symbol);
        String displayName;

        if (coinId.isPresent()) {
            displayName = coinId.get().getName();
            targetPriceProvider.updateTargetPrice(
                    coinId.get(), buyTarget, sellTarget, FILE_PATH_COINS);
        } else if (stockSymbol.isPresent()) {
            displayName = stockSymbol.get().getDisplayName();
            targetPriceProvider.updateTargetPrice(
                    stockSymbol.get(), buyTarget, sellTarget, FILE_PATH_STOCKS);
        } else {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }

        telegramClient.sendMessage(
                "All set!\n"
                        + "Updated "
                        + command.getSubCommand()
                        + " price for "
                        + displayName
                        + " to "
                        + command.getTarget()
                        + ".");
    }
}
