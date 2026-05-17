package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.FeatureToggle;
import org.tradelite.service.FeatureToggleService;

@Slf4j
@Component
public class ToggleCommandProcessor implements TelegramCommandProcessor<ToggleCommand> {

    private final FeatureToggleService featureToggleService;
    private final TelegramGateway telegramGateway;

    @Autowired
    public ToggleCommandProcessor(
            FeatureToggleService featureToggleService, TelegramGateway telegramGateway) {
        this.featureToggleService = featureToggleService;
        this.telegramGateway = telegramGateway;
    }

    @Override
    public boolean canProcess(TelegramCommand command) {
        return command instanceof ToggleCommand;
    }

    @Override
    public void processCommand(ToggleCommand command) {
        if (command.getFeatureName() == null) {
            showAllToggles();
        } else {
            setToggle(command.getFeatureName(), command.getEnabled());
        }
    }

    private void showAllToggles() {
        StringBuilder sb = new StringBuilder("Feature Toggles:\n");
        for (FeatureToggle toggle : FeatureToggle.values()) {
            boolean enabled = featureToggleService.isEnabled(toggle);
            sb.append(toggle.getKey()).append(": ").append(enabled ? "ON" : "OFF").append("\n");
        }
        telegramGateway.sendMessage(sb.toString().trim());
    }

    private void setToggle(String featureName, boolean enabled) {
        FeatureToggle matchedToggle = findToggleByKey(featureName);
        if (matchedToggle == null) {
            telegramGateway.sendMessage("Unknown feature: " + featureName);
            return;
        }

        featureToggleService.setToggle(matchedToggle, enabled);
        telegramGateway.sendMessage(featureName + ": " + (enabled ? "ON" : "OFF"));
    }

    private FeatureToggle findToggleByKey(String key) {
        for (FeatureToggle toggle : FeatureToggle.values()) {
            if (toggle.getKey().equals(key)) {
                return toggle;
            }
        }
        return null;
    }
}
