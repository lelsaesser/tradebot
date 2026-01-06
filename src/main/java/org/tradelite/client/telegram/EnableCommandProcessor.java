package org.tradelite.client.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradelite.config.ConfigurationService;

@Component
@RequiredArgsConstructor
public class EnableCommandProcessor implements TelegramCommandProcessor<EnableCommand> {

    public static final String FEATURE_DEMO_TRADING = "demotrading";

    private final ConfigurationService configurationService;
    private final TelegramClient telegramClient;

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof EnableCommand;
    }

    @Override
    public void processCommand(EnableCommand command) {
        String feature = command.feature().toLowerCase();

        if (!FEATURE_DEMO_TRADING.equals(feature)) {
            telegramClient.sendMessage("❌ Unknown feature: " + command.feature());
            return;
        }

        configurationService.enable(feature);
        telegramClient.sendMessage(
                "✅ Demo Trading enabled\n\nThe bot will now execute simulated trades.");
    }
}
