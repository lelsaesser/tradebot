package org.tradelite.client.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradelite.config.ConfigurationService;

@Component
@RequiredArgsConstructor
public class DisableCommandProcessor implements TelegramCommandProcessor<DisableCommand> {

    public static final String FEATURE_DEMO_TRADING = "demotrading";

    private final ConfigurationService configurationService;
    private final TelegramClient telegramClient;

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof DisableCommand;
    }

    @Override
    public void processCommand(DisableCommand command) {
        String feature = command.feature().toLowerCase();

        if (!FEATURE_DEMO_TRADING.equals(feature)) {
            telegramClient.sendMessage("❌ Unknown feature: " + command.feature());
            return;
        }

        configurationService.disable(feature);
        telegramClient.sendMessage(
                "🛑 Demo Trading disabled\n\nThe bot will no longer execute simulated trades.");
    }
}
