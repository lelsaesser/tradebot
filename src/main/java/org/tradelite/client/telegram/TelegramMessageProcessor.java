package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TelegramMessageProcessor {

    private final TelegramClient telegramClient;
    private final TelegramCommandDispatcher telegramCommandDispatcher;
    private final TelegramMessageTracker telegramMessageTracker;

    @Autowired
    public TelegramMessageProcessor(TelegramClient telegramClient, TelegramCommandDispatcher telegramCommandDispatcher, TelegramMessageTracker telegramMessageTracker) {
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

            Optional<TelegramCommand> command = parseMessage(chatUpdate);
            command.ifPresent(telegramCommandDispatcher::dispatch);

            telegramMessageTracker.setLastProcessedMessageId(messageId);
        }
    }

    private Optional<TelegramCommand> parseMessage(TelegramUpdateResponse update) {
        String messageText = update.getMessage().getText();
        if (messageText != null && messageText.startsWith("/set")) {
            String[] parts = messageText.split("\\s+");
            if (parts.length == 4) {
                String subCommand = parts[1]; // buy or sell
                String symbol = parts[2];
                double target = Double.parseDouble(parts[3]);

                Optional<SetCommand> cmd = buildSetCommand(subCommand, symbol, target);
                if (cmd.isPresent()) {
                    log.info("Received set message for command: {}, symbol: {}, target: {}", subCommand, symbol, target);
                }
                return cmd.map(telegramCommand -> telegramCommand);
            }
        }
        return Optional.empty();
    }

    protected Optional<SetCommand> buildSetCommand(String subCommand, String symbol, double target) {
        String errorMessageCommandFormat = "Invalid command format. Use /set <buy|sell> <symbol> <target>";
        String errorMessageInvalidSymbol = "Invalid symbol. Please provide a valid symbol.";
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
            telegramClient.sendMessage(errorMessageInvalidSymbol);
            return Optional.empty();
        }
        Optional<CoinId> coinId = CoinId.fromString(symbol);
        Optional<StockSymbol> stockSymbol = StockSymbol.fromString(symbol);
        if (coinId.isEmpty() && stockSymbol.isEmpty()) {
            telegramClient.sendMessage(errorMessageInvalidSymbol);
            return Optional.empty();
        }
        if (target <= 0) {
            telegramClient.sendMessage(errorMessageInvalidTarget);
            return Optional.empty();
        }

        return Optional.of(new SetCommand(subCommand, symbol, target));
    }

}
