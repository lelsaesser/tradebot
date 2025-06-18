package org.tradelite.client.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPriceProvider;

import java.util.Objects;
import java.util.Optional;

import static org.tradelite.common.TargetPriceProvider.FILE_PATH_COINS;
import static org.tradelite.common.TargetPriceProvider.FILE_PATH_STOCKS;

@Component
public class SetCommandProcessor implements TelegramCommandProcessor<SetCommand> {

    private final TargetPriceProvider targetPriceProvider;
    private final TelegramClient telegramClient;

    @Autowired
    public SetCommandProcessor(TargetPriceProvider targetPriceProvider, TelegramClient telegramClient) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
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
        Optional<StockSymbol> stockSymbol = StockSymbol.fromString(symbol);

        if (coinId.isPresent()) {
            targetPriceProvider.updateTargetPrice(coinId.get(), buyTarget, sellTarget, FILE_PATH_COINS);
        } else if (stockSymbol.isPresent()) {
            targetPriceProvider.updateTargetPrice(stockSymbol.get(), buyTarget, sellTarget, FILE_PATH_STOCKS);
        } else {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }

        telegramClient.sendMessage("All set!\n" +
                "Updated " + command.getSubCommand() + " price for " + symbol.toUpperCase() + " to " + command.getTarget() + ".");
    }
}
