package org.tradelite.client.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.dto.TelegramUpdateResponse;

import java.util.List;

@Slf4j
@Component
public class TelegramMessageProcessor {

    public void parseMessage(List<TelegramUpdateResponse> chatUpdates) {
        for (TelegramUpdateResponse update : chatUpdates) {
            if (update.getMessage() == null) {
                continue;
            }
            String messageText = update.getMessage().getText();
            if (messageText != null && messageText.startsWith("/set")) {
                String[] parts = messageText.split("\\s+");
                if (parts.length == 4) {
                    String subCommand = parts[1]; // buytarget or selltarget
                    String symbol = parts[2];     // e.g. HOOD
                    double target = Double.parseDouble(parts[3]); // e.g. 52

                    log.info("Received set message for command: {}, symbol: {}, target: {}", subCommand, symbol, target);
                }
            }
        }
    }

}
