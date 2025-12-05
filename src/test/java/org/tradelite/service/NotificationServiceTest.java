package org.tradelite.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.discord.DiscordClient;
import org.tradelite.client.telegram.TelegramClient;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private TelegramClient telegramClient;
    @Mock private DiscordClient discordClient;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(telegramClient, discordClient);
    }

    @Test
    void testSendNotification_success() {
        String message = "Test notification";

        notificationService.sendNotification(message);

        verify(telegramClient).sendMessage(message);
        verify(discordClient).sendMessage(message);
    }

    @Test
    void testSendNotification_telegramFailsDiscordSucceeds() {
        String message = "Test notification";
        doThrow(new RuntimeException("Telegram error")).when(telegramClient).sendMessage(message);

        notificationService.sendNotification(message);

        verify(telegramClient).sendMessage(message);
        verify(discordClient).sendMessage(message);
    }

    @Test
    void testSendNotification_discordFailsTelegramSucceeds() {
        String message = "Test notification";
        doThrow(new RuntimeException("Discord error")).when(discordClient).sendMessage(message);

        notificationService.sendNotification(message);

        verify(telegramClient).sendMessage(message);
        verify(discordClient).sendMessage(message);
    }

    @Test
    void testSendNotification_bothFail() {
        String message = "Test notification";
        doThrow(new RuntimeException("Telegram error")).when(telegramClient).sendMessage(message);
        doThrow(new RuntimeException("Discord error")).when(discordClient).sendMessage(message);

        notificationService.sendNotification(message);

        verify(telegramClient).sendMessage(message);
        verify(discordClient).sendMessage(message);
    }

    @Test
    void testSendTelegramNotification_success() {
        String message = "Telegram only message";

        notificationService.sendTelegramNotification(message);

        verify(telegramClient).sendMessage(message);
        verifyNoInteractions(discordClient);
    }

    @Test
    void testSendTelegramNotification_failure() {
        String message = "Telegram only message";
        doThrow(new RuntimeException("Telegram error")).when(telegramClient).sendMessage(message);

        notificationService.sendTelegramNotification(message);

        verify(telegramClient).sendMessage(message);
        verifyNoInteractions(discordClient);
    }

    @Test
    void testSendDiscordNotification_success() {
        String message = "Discord only message";

        notificationService.sendDiscordNotification(message);

        verify(discordClient).sendMessage(message);
        verifyNoInteractions(telegramClient);
    }

    @Test
    void testSendDiscordNotification_failure() {
        String message = "Discord only message";
        doThrow(new RuntimeException("Discord error")).when(discordClient).sendMessage(message);

        notificationService.sendDiscordNotification(message);

        verify(discordClient).sendMessage(message);
        verifyNoInteractions(telegramClient);
    }
}
