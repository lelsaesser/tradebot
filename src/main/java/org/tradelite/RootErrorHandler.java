package org.tradelite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;

@Slf4j
@Component
public class RootErrorHandler {

    private final TelegramGateway telegramClient;

    @Autowired
    public RootErrorHandler(TelegramGateway telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void run(ThrowingRunnable body) {
        runWithStatus(body);
    }

    public boolean runWithStatus(ThrowingRunnable body) {
        try {
            body.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Operation was interrupted: {}", e.getMessage());
            String message = "⏸️ *Operation Interrupted!* Check application logs for details.";
            telegramClient.sendMessage(message);
            return false;
        } catch (Exception e) {
            log.error(e.getMessage(), e);

            String exceptionType = e.getClass().getSimpleName();
            String exceptionMessage = e.getMessage();

            StringBuilder message = new StringBuilder();
            message.append("🚨 *Exception Alert!*\n\n");
            message.append("*Type:* `").append(exceptionType).append("`\n");

            if (exceptionMessage != null && exceptionMessage.length() <= 100) {
                message.append("*Message:* `")
                        .append(escapeMarkdown(exceptionMessage))
                        .append("`\n");
            }

            telegramClient.sendMessage(message.toString());
            return false;
        }
    }

    private String escapeMarkdown(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}
