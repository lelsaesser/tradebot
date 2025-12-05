package org.tradelite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.client.discord.DiscordClient;
import org.tradelite.client.telegram.TelegramClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TelegramClient telegramClient;
    private final DiscordClient discordClient;

    /**
     * Send a message to both Telegram and Discord channels
     *
     * @param message The message to send
     */
    public void sendNotification(String message) {
        log.info("Sending notification to all channels: {}", message);

        // Send to Telegram
        try {
            telegramClient.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification: {}", e.getMessage());
        }

        // Send to Discord
        try {
            discordClient.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send Discord notification: {}", e.getMessage());
        }
    }

    /**
     * Send a message only to Telegram
     *
     * @param message The message to send
     */
    public void sendTelegramNotification(String message) {
        try {
            telegramClient.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send Telegram notification: {}", e.getMessage());
        }
    }

    /**
     * Send a message only to Discord
     *
     * @param message The message to send
     */
    public void sendDiscordNotification(String message) {
        try {
            discordClient.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send Discord notification: {}", e.getMessage());
        }
    }
}
