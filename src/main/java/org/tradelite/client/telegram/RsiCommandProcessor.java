package org.tradelite.client.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradelite.service.RsiService;

@Component
@RequiredArgsConstructor
public class RsiCommandProcessor implements TelegramCommandProcessor<RsiCommand> {

    private final RsiService rsiService;
    private final TelegramClient telegramClient;

    @Override
    public void processCommand(RsiCommand command) {
        rsiService
                .getCurrentRsi(command.getSymbol())
                .ifPresentOrElse(
                        rsi -> {
                            String message =
                                    String.format(
                                            "RSI for %s is %.2f",
                                            command.getSymbol().getDisplayName(), rsi);
                            telegramClient.sendMessage(message);
                        },
                        () -> {
                            String message =
                                    String.format(
                                            "Not enough data to calculate RSI for %s",
                                            command.getSymbol().getDisplayName());
                            telegramClient.sendMessage(message);
                        });
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof RsiCommand;
    }
}
