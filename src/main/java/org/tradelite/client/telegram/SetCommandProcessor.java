package org.tradelite.client.telegram;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_COINS;
import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.StockSymbolRegistry;

@Component
public class SetCommandProcessor implements TelegramCommandProcessor<SetCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;

    @Autowired
    public SetCommandProcessor(
            TargetPriceProvider targetPriceProvider,
            TelegramGateway telegramClient,
            StockSymbolRegistry stockSymbolRegistry) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
        this.stockSymbolRegistry = stockSymbolRegistry;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof SetCommand;
    }

    @Override
    public void processCommand(SetCommand command) {
        Double sellTarget = null;
        Double buyTarget = null;

        if (Objects.equals(command.getSubCommand(), "buy")) {
            buyTarget = command.getTarget();
        } else if (Objects.equals(command.getSubCommand(), "sell")) {
            sellTarget = command.getTarget();
        } else {
            throw new IllegalArgumentException("Invalid sub-command: " + command.getSubCommand());
        }

        String symbol = command.getSymbol();
        Optional<CoinId> coinId = CoinId.fromString(symbol);
        Optional<StockSymbol> stockSymbol = stockSymbolRegistry.fromString(symbol);
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
