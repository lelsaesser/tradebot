package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TelegramMessageProcessor {

    private final TelegramClient telegramClient;
    private final TelegramCommandDispatcher telegramCommandDispatcher;

    @Autowired
    public TelegramMessageProcessor(TelegramClient telegramClient, TelegramCommandDispatcher telegramCommandDispatcher) {
        this.telegramClient = telegramClient;
        this.telegramCommandDispatcher = telegramCommandDispatcher;
    }

    public void processUpdates(List<TelegramUpdateResponse> chatUpdates) {
        Optional<TelegramCommand> command = parseMessage(chatUpdates);
        command.ifPresent(telegramCommandDispatcher::dispatch);
    }

    private Optional<TelegramCommand> parseMessage(List<TelegramUpdateResponse> chatUpdates) {
        for (TelegramUpdateResponse update : chatUpdates) {
            if (update.getMessage() == null) {
                continue;
            }
            String messageText = update.getMessage().getText();
            if (messageText != null && messageText.startsWith("/set")) {
                String[] parts = messageText.split("\\s+");
                if (parts.length == 4) {
                    String subCommand = parts[1]; // buy or sell
                    String symbol = parts[2];
                    double target = Double.parseDouble(parts[3]);

                    Optional<SetCommand> cmd = buildSetCommand(subCommand, symbol, target).map(telegramCommand -> telegramCommand);
                    if (cmd.isPresent()) {
                        log.info("Received set message for command: {}, symbol: {}, target: {}", subCommand, symbol, target);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<SetCommand> buildSetCommand(String subCommand, String symbol, double target) {
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
        List<String> allSymbolNames = CoinId.getAll().stream()
                .map(CoinId::getName)
                .toList();
        if (!allSymbolNames.contains(symbol)) {
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
