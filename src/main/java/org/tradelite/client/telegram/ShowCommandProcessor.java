package org.tradelite.client.telegram;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@Component
public class ShowCommandProcessor implements TelegramCommandProcessor<ShowCommand> {

    private String errorMessage = "";
    private final TargetPriceProvider targetPriceProvider;
    private final TelegramGateway telegramClient;

    @Autowired
    public ShowCommandProcessor(
            TargetPriceProvider targetPriceProvider, TelegramGateway telegramClient) {
        this.targetPriceProvider = targetPriceProvider;
        this.telegramClient = telegramClient;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof ShowCommand;
    }

    @Override
    public void processCommand(ShowCommand command) {
        if (!isValidCommand(command)) {
            telegramClient.sendMessage(errorMessage);
            return;
        }

        String responseMessage = "";
        List<TargetPrice> coinPrices = targetPriceProvider.getCoinTargetPrices();
        List<TargetPrice> stockPrices = targetPriceProvider.getStockTargetPrices();

        if (command.getSubCommand().equals(ShowCommandOptions.ALL.getName())) {
            responseMessage = builtResponseMessage(coinPrices, stockPrices);
        } else if (command.getSubCommand().equals(ShowCommandOptions.COINS.getName())) {
            responseMessage = builtResponseMessage(coinPrices, List.of());
        } else if (command.getSubCommand().equals(ShowCommandOptions.STOCKS.getName())) {
            responseMessage = builtResponseMessage(List.of(), stockPrices);
        }

        telegramClient.sendMessage(responseMessage);
    }

    protected boolean isValidCommand(ShowCommand command) {
        errorMessage = "";
        if (command.getSubCommand() == null || command.getSubCommand().isEmpty()) {
            errorMessage =
                    "Sub-command is required. Use "
                            + ShowCommandOptions.ALL.getName()
                            + ", "
                            + ShowCommandOptions.COINS.getName()
                            + ", or "
                            + ShowCommandOptions.STOCKS.getName()
                            + ".";
            return false;
        }
        if (!command.getSubCommand().equals("coins")
                && !command.getSubCommand().equals("stocks")
                && !command.getSubCommand().equals("all")) {
            errorMessage =
                    "Invalid sub-command: "
                            + command.getSubCommand()
                            + ". Use 'coins', 'stocks', or 'all'.";
            return false;
        }
        return true;
    }

    protected String builtResponseMessage(
            List<TargetPrice> coinPrices, List<TargetPrice> stockPrices) {
        StringBuilder message = new StringBuilder();

        message.append("Current monitoring watchlist contains following symbols:%n%n".formatted());

        if (!coinPrices.isEmpty()) {
            message.append("*Cryptos:*").append("%n".formatted());
            message.append("```").append("%n".formatted());
            message.append(
                    String.format("%-12s %-12s %-12s%n", "Symbol", "Buy Target", "Sell Target"));
            for (TargetPrice price : coinPrices) {
                message.append(
                        String.format(
                                "%-12s %-12.2f %-12.2f%n",
                                price.getSymbol(), price.getBuyTarget(), price.getSellTarget()));
            }
            message.append("```").append("%n%n".formatted());
        }

        if (!stockPrices.isEmpty()) {
            message.append("*Stocks:*").append("%n".formatted());
            message.append("```").append("%n".formatted());
            message.append(
                    String.format("%-12s %-12s %-12s%n", "Symbol", "Buy Target", "Sell Target"));
            for (TargetPrice price : stockPrices) {
                message.append(
                        String.format(
                                "%-12s %-12.2f %-12.2f%n",
                                price.getSymbol(), price.getBuyTarget(), price.getSellTarget()));
            }
            message.append("```");
        }

        return message.toString();
    }
}
