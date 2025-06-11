package org.tradelite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramClient;

@Component
public class RootErrorHandler {

    private final TelegramClient telegramClient;

    @Autowired
    public RootErrorHandler(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void run(ThrowingRunnable body) {
        try {
            body.run();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            String message = "⏸️ *Operation Interrupted!* Check application logs for details.";
            telegramClient.sendMessage(message);
        } catch (Exception e) {
            String exceptionType = e.getClass().getSimpleName();
            String exceptionMessage = e.getMessage();

            StringBuilder message = new StringBuilder();
            message.append("🚨 *Exception Alert!*\n\n");
            message.append("*Type:* `").append(exceptionType).append("`\n");

            if (exceptionMessage != null && exceptionMessage.length() <= 100) {
                message.append("*Message:* `").append(escapeMarkdown(exceptionMessage)).append("`\n");
            }

            telegramClient.sendMessage(message.toString());
        }
    }

    private String escapeMarkdown(String text) {
        return text
                .replace("_", "\\_")
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
