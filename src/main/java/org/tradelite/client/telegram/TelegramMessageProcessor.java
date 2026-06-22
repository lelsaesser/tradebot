package org.tradelite.client.telegram;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramMessage;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TickerSymbol;
import org.tradelite.config.TradebotTelegramProperties;

@Slf4j
@Component
public class TelegramMessageProcessor {

    private static final String ERROR_MSG_INVALID_SYMBOL =
            "Invalid symbol. Please provide a valid symbol.";

    private final TelegramGateway telegramClient;
    private final TelegramCommandDispatcher telegramCommandDispatcher;
    private final TelegramMessageTracker telegramMessageTracker;
    private final SymbolRegistry symbolRegistry;

    /**
     * Chat ID authorized to issue commands. Parsed once at construction time from {@link
     * TradebotTelegramProperties#getGroupChatId()} — a malformed value fails bean creation rather
     * than silently disabling validation at runtime.
     */
    private final long allowedChatId;

    @Autowired
    public TelegramMessageProcessor(
            TelegramGateway telegramClient,
            TelegramCommandDispatcher telegramCommandDispatcher,
            TelegramMessageTracker telegramMessageTracker,
            SymbolRegistry symbolRegistry,
            TradebotTelegramProperties telegramProperties) {
        this.telegramClient = telegramClient;
        this.telegramCommandDispatcher = telegramCommandDispatcher;
        this.telegramMessageTracker = telegramMessageTracker;
        this.symbolRegistry = symbolRegistry;
        this.allowedChatId = Long.parseLong(telegramProperties.getGroupChatId());
    }

    public void processUpdates(List<TelegramUpdateResponse> chatUpdates) {
        long lastProcessedMessageId = telegramMessageTracker.getLastProcessedMessageId();

        for (TelegramUpdateResponse chatUpdate : chatUpdates) {

            TelegramMessage msg = chatUpdate.getMessage();
            if (msg == null
                    || msg.getText() == null
                    || msg.getChat() == null
                    || msg.getChat().getChatId() == null) {
                continue;
            }

            long messageId = msg.getMessageId();
            if (messageId <= lastProcessedMessageId) {
                log.info("Skipping already processed message with ID: {}", messageId);
                continue;
            }

            long incomingChatId = msg.getChat().getChatId();
            if (incomingChatId != allowedChatId) {
                // Closes the leaked-token attack vector: even with the bot token an attacker
                // cannot drive commands unless their message arrives in the configured group.
                // Log the command prefix only — full text is attacker-controlled and would
                // expand the log-injection surface (#465).
                String prefix = msg.getText().split("\\s+", 2)[0];
                log.warn(
                        "Rejected command from unexpected chat id={} prefix={}",
                        incomingChatId,
                        prefix);
                telegramMessageTracker.setLastProcessedMessageId(messageId);
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
        } else if (messageText != null && messageText.toLowerCase().startsWith("/data")) {
            Optional<DataResetCommand> dataResetCommand = parseDataResetCommand(messageText);
            if (dataResetCommand.isPresent()) {
                log.info("Received data reset command: {}", dataResetCommand.get());
                return Optional.of(dataResetCommand.get());
            }
        } else if (messageText != null && messageText.toLowerCase().startsWith("/toggle")) {
            Optional<ToggleCommand> toggleCommand = parseToggleCommand(messageText);
            if (toggleCommand.isPresent()) {
                log.info("Received toggle command: {}", toggleCommand.get());
                return Optional.of(toggleCommand.get());
            }
        }
        return Optional.empty();
    }

    protected Optional<SetCommand> buildSetCommand(
            String subCommand, String symbol, double target) {
        String errorMessageCommandFormat =
                "Invalid command format. Use /set `<buy|sell>` `<symbol>` `<target>`";
        String errorMessageInvalidTarget = "Invalid target. Please provide a valid target price.";

        if (subCommand == null) {
            telegramClient.sendMessage(errorMessageCommandFormat);
            return Optional.empty();
        }
        if (!subCommand.equalsIgnoreCase("buy") && !subCommand.equalsIgnoreCase("sell")) {
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
        if (parts.length != 3) {
            telegramClient.sendMessage(
                    "Invalid command format. Use /add `<TICKER>` `<Display_Name>`");
            return Optional.empty();
        }

        String ticker = parts[1];
        String displayName = parts[2].replace("_", " ");

        return Optional.of(new AddCommand(ticker, displayName, 0.0, 0.0));
    }

    protected Optional<RemoveCommand> parseRemoveCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 2) {
            telegramClient.sendMessage("Invalid command format. Use /remove `<TICKER>`");
            return Optional.empty();
        }

        String ticker = parts[1];
        return Optional.of(new RemoveCommand(ticker));
    }

    protected Optional<TickerSymbol> parseTickerSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return Optional.empty();
        }
        Optional<CoinId> coinId = CoinId.fromString(symbol);
        //noinspection OptionalIsPresent
        if (coinId.isPresent()) {
            return Optional.of(coinId.get());
        }
        return symbolRegistry.fromString(symbol).map(s -> s);
    }

    protected Optional<RsiCommand> parseRsiCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 2) {
            telegramClient.sendMessage("Invalid command format. Use /rsi `<symbol>`");
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

    protected Optional<DataResetCommand> parseDataResetCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length != 3 || !parts[1].equalsIgnoreCase("reset")) {
            telegramClient.sendMessage("Invalid command format. Use /data reset `<SYMBOL>`");
            return Optional.empty();
        }
        String ticker = parts[2].toUpperCase();
        return Optional.of(new DataResetCommand(ticker));
    }

    protected Optional<ToggleCommand> parseToggleCommand(String commandText) {
        String[] parts = commandText.split("\\s+");
        if (parts.length == 1) {
            return Optional.of(new ToggleCommand(null, null));
        }
        if (parts.length == 3) {
            String featureName = parts[1];
            String onOff = parts[2].toLowerCase();
            if ("on".equals(onOff)) {
                return Optional.of(new ToggleCommand(featureName, true));
            } else if ("off".equals(onOff)) {
                return Optional.of(new ToggleCommand(featureName, false));
            }
        }
        telegramClient.sendMessage(
                "Invalid command format. Use /toggle `<feature_name>` `<on|off>`");
        return Optional.empty();
    }
}
