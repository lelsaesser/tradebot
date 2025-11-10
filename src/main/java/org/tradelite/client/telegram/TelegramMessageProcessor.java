package org.tradelite.client.telegram;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TickerSymbol;

@Slf4j
@Component
public class TelegramMessageProcessor {

    private static final String ERROR_MSG_INVALID_SYMBOL =
            "Invalid symbol. Please provide a valid symbol.";

    private final TelegramClient telegramClient;
    private final TelegramCommandDispatcher telegramCommandDispatcher;
    private final TelegramMessageTracker telegramMessageTracker;

    @Autowired
    public TelegramMessageProcessor(
            TelegramClient telegramClient,
            TelegramCommandDispatcher telegramCommandDispatcher,
            TelegramMessageTracker telegramMessageTracker) {
        this.telegramClient = telegramClient;
        this.telegramCommandDispatcher = telegramCommandDispatcher;
        this.telegramMessageTracker = telegramMessageTracker;
    }

    public void processUpdates(List<TelegramUpdateResponse> chatUpdates) {
        long lastProcessedMessageId = telegramMessageTracker.getLastProcessedMessageId();

        for (TelegramUpdateResponse chatUpdate : chatUpdates) {

            if (chatUpdate.getMessage() == null || chatUpdate.getMessage().getText() == null) {
                continue;
            }

            long messageId = chatUpdate.getMessage().getMessageId();
            if (messageId <= lastProcessedMessageId) {
                log.info("Skipping already processed message with ID: {}", messageId);
                continue;
            }

            try {
                Optional<TelegramCommand> command = parseMessage(chatUpdate);
                command.ifPresent(telegramCommandDispatcher::dispatch);
            } finally {
                telegramMessageTracker.setLastProcessedMessageId(messageId);
            }
        }
    }

    protected Optional<TelegramCommand> parseMessage(TelegramUpdateResponse update) {
        String messageText = update.getMessage().getText();
        if (messageText != null && messageText.toLowerCase().startsWith("/set")) {
            String[] parts = messageText.split("\\s+");
            if (parts.length == 4) {
                String subCommand = parts[1]; // buy or sell
                String symbol = parts[2];
                double target = Double.parseDouble(parts[3]);

                Optional<SetCommand> cmd = buildSetCommand(subCommand, symbol, target);
                if (cmd.isPresent()) {
                    log.info(
                            "Received set command: {}, symbol: {}, target: {}",
                            subCommand,
                            symbol,
                            target);
                    return Optional.of(cmd.get());
                }
            }
        } else if (messageText != null && messageText.toLowerCase().startsWith("/show")) {
            String[] parts = messageText.split("\\s+");
            if (parts.length == 2) {
                String subCommand = parts[1]; // coins / stocks / all
                ShowCommand showCommand = new ShowCommand(subCommand);
                log.info("Received show command for sub-command: {}", subCommand);
                return Optional.of(showCommand);
            }
        } else if (messageText != null && messageText.toLowerCase().startsWith("/add")) {
            Optional<AddCommand> addCommand = parseAddCommand(messageText);
            if (addCommand.isPresent()) {
                log.info("Received add command: {}", addCommand.get());
                return Optional.of(addCommand.get());
            }
        } else if (messageText != null && messageText.toLowerCase().startsWith("/remove")) {
            Optional<RemoveCommand> removeCommand = parseRemoveCommand(messageText);
            if (removeCommand.isPresent()) {
                log.info("Received remove command: {}", removeCommand.get());
                return Optional.of(removeCommand.get());
            }
        } else if (messageText != null && messageText.toLowerCase().startsWith("/rsi")) {
            Optional<RsiCommand> rsiCommand = parseRsiCommand(messageText);
            if (rsiCommand.isPresent()) {
                log.info("Received rsi command: {}", rsiCommand.get());
                return Optional.of(rsiCommand.get());
            }
        }
        return Optional.empty();
    }

    protected Optional<SetCommand> buildSetCommand(
            String subCommand, String symbol, double target) {
        String errorMessageCommandFormat =
                "Invalid command format. Use /set <buy|sell> <symbol> <target>";
        String errorMessageInvalidTarget = "Invalid target. Please provide a valid target price.";

        if (subCommand == null) {
            telegramClient.sendMessage(errorMessageCommandFormat);
            return Optional.empty();
        }
        if (!subCommand.equals("buy") && !subCommand.equals("sell")) {
            telegramClient.sendMessage(errorMessageCommandFormat);
            return Optional.empty();
        }
        if (symbol == null || symbol.isEmpty()) {
            telegramClient.sendMessage(ERROR_MSG_INVALID_SYMBOL);
            return Optional.empty();
        }
        if (parseTickerSymbol(symbol).isEmpty()) {
            telegramClient.sendMessage(ERROR_MSG_INVALID_SYMBOL);
            return Optional.empty();
        }
        if (target < 0) {
            telegramClient.sendMessage(errorMessageInvalidTarget);
            return Optional.empty();
        }

        return Optional.of(new SetCommand(subCommand, symbol, target));
    }

    protected Optional<AddCommand> parseAddCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 4) {
            telegramClient.sendMessage(
                    "Invalid command format. Use /add <symbol> <buyTarget> <sellTarget>");
            return Optional.empty();
        }

        String symbol = parts[1];
        Optional<Double> buyTarget;
        Optional<Double> sellTarget;

        buyTarget = tryParseDouble(parts[2]);
        sellTarget = tryParseDouble(parts[3]);
        if (buyTarget.isEmpty() || sellTarget.isEmpty()) {
            telegramClient.sendMessage("Invalid target price. Please provide valid numbers.");
            return Optional.empty();
        }

        if (buyTarget.get() < 0 || sellTarget.get() < 0) {
            telegramClient.sendMessage("Target prices must be non-negative.");
            return Optional.empty();
        }

        Optional<TickerSymbol> tickerSymbol = parseTickerSymbol(symbol);
        if (tickerSymbol.isEmpty()) {
            telegramClient.sendMessage(ERROR_MSG_INVALID_SYMBOL);
            return Optional.empty();
        }

        return Optional.of(
                new AddCommand(
                        tickerSymbol.get(),
                        buyTarget.get(),
                        sellTarget.get(),
                        tickerSymbol.get().getSymbolType()));
    }

    protected Optional<RemoveCommand> parseRemoveCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 2) {
            telegramClient.sendMessage("Invalid command format. Use /remove <symbol>");
            return Optional.empty();
        }

        String symbol = parts[1];
        Optional<TickerSymbol> tickerSymbol = parseTickerSymbol(symbol);
        if (tickerSymbol.isEmpty()) {
            telegramClient.sendMessage(ERROR_MSG_INVALID_SYMBOL);
            return Optional.empty();
        }

        return Optional.of(
                new RemoveCommand(tickerSymbol.get(), tickerSymbol.get().getSymbolType()));
    }

    protected Optional<Double> tryParseDouble(String value) {
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException | NullPointerException _) {
            return Optional.empty();
        }
    }

    protected Optional<TickerSymbol> parseTickerSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return Optional.empty();
        }
        Optional<CoinId> coinId = CoinId.fromString(symbol);
        Optional<StockSymbol> stockSymbol = StockSymbol.fromString(symbol);
        if (coinId.isPresent()) {
            return Optional.of(coinId.get());
        } else if (stockSymbol.isPresent()) {
            return Optional.of(stockSymbol.get());
        }
        return Optional.empty();
    }

    protected Optional<RsiCommand> parseRsiCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 2) {
            telegramClient.sendMessage("Invalid command format. Use /rsi <symbol>");
            return Optional.empty();
        }

        String symbol = parts[1];
        Optional<TickerSymbol> tickerSymbol = parseTickerSymbol(symbol);
        if (tickerSymbol.isEmpty()) {
            telegramClient.sendMessage(ERROR_MSG_INVALID_SYMBOL);
            return Optional.empty();
        }

        return Optional.of(new RsiCommand(tickerSymbol.get()));
    }
}
